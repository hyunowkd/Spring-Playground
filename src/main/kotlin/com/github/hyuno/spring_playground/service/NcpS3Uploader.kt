package com.github.hyuno.spring_playground.service

import com.amazonaws.SdkClientException
import com.amazonaws.services.s3.model.*
import com.amazonaws.util.IOUtils
import com.github.hyuno.spring_playground.config.NcpS3Config
import com.github.hyuno.spring_playground.config.S3Config
import com.drew.imaging.FileType
import com.drew.imaging.FileTypeDetector
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.jpeg.JpegDirectory
import com.github.hyuno.spring_playground.DTO.ChatVideoResponseDTO
import com.github.hyuno.spring_playground.errorHandler.CommonException
import jakarta.transaction.Transactional
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.jcodec.api.FrameGrab
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.Picture
import org.jcodec.scale.AWTUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream


@Service
class NcpS3Uploader(
    @Autowired private val ncp: NcpS3Config,
    @Autowired private val s3 : S3Config,
    ) {

    @Value("\${ncp.bucketName}")
    private val bucketName: String? = null

    @Value("\${s3.bucketName}")
    private val videoBucketName : String? = null

    fun calculateImageCompressionRatio(input: Long): Float {
        val standard = 3000_000f
        val result = (standard / input)

        if (standard >= input) {
            return 1f
        }

        return result
    }

    fun fileLocation(folderName: String, fileName: String): String {
        return folderName.plus("/").plus(fileName)
    }

    private fun detectFileType(bytes: ByteArray): FileType {
        return FileTypeDetector.detectFileType(ByteArrayInputStream(bytes))
    }
    private fun isUnsupportedFormat(fileType: FileType): Boolean {
        return fileType == FileType.Heif || fileType == FileType.WebP
    }
    private fun processImage(bytes: ByteArray, fileType: FileType, originalSize: Long): File {
        val originalImage = ImageIO.read(ByteArrayInputStream(bytes))
        val rotated = rotateImageIfNeeded(originalImage, bytes)
        val resized = resizeImageIfNecessary(rotated)
        return compressImage(resized, fileType, originalSize)
    }
    private fun resizeImageIfNecessary(image: BufferedImage, maxWidth: Int = 2048): BufferedImage {
        if (image.width <= maxWidth) return image

        val ratio = maxWidth.toDouble() / image.width.toDouble()
        val newWidth = maxWidth
        val newHeight = (image.height * ratio).toInt()

        val resized = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = resized.createGraphics()
        graphics.drawImage(image, 0, 0, newWidth, newHeight, null)
        graphics.dispose()

        return resized
    }
    private fun compressImage(image: BufferedImage, fileType: FileType, originalSize: Long): File {
        val compressedFile = File.createTempFile("compressed_", ".jpg")

        val outputStream = FileOutputStream(compressedFile)
        val ios = ImageIO.createImageOutputStream(outputStream)

        val writers = ImageIO.getImageWritersByFormatName("jpg")
        val writer = writers.next()
        writer.output = ios

        val param = writer.defaultWriteParam
        param.compressionMode = ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = getCompressionQuality(originalSize)

        writer.write(null, IIOImage(image, null, null), param)

        ios.close()
        writer.dispose()
        outputStream.close()

        return compressedFile
    }

    private fun getCompressionQuality(size: Long): Float {
        return when {
            size > 5 * 1024 * 1024 -> 0.5f // 5MB 이상이면 많이 압축
            size > 2 * 1024 * 1024 -> 0.7f
            else -> 0.9f
        }
    }

    private fun uploadRawImageToS3(bytes: ByteArray, folderName: String, fileType: FileType): String {
        val extension = fileType.name.lowercase()
        val key = fileLocation(folderName, "${UUID.randomUUID()}.$extension")

        val metadata = ObjectMetadata().apply {
            contentLength = bytes.size.toLong()
            contentType = "image/$extension"
        }

        ByteArrayInputStream(bytes).use {
            ncp.assetNcpS3Client().putObject(PutObjectRequest(bucketName, key, it, metadata))
        }

        return key
    }
    private fun uploadFileToS3(file: File, folderName: String, extension: String): String {
        val key = fileLocation(folderName, "${UUID.randomUUID()}.$extension")

        val metadata = ObjectMetadata().apply {
            contentLength = file.length()
            contentType = "image/$extension"
        }

        file.inputStream().use {
            ncp.assetNcpS3Client().putObject(PutObjectRequest(bucketName, key, it, metadata))
        }

        file.delete()
        return key
    }

    private fun rotateImageIfNeeded(image: BufferedImage, imageBytes: ByteArray): BufferedImage {
        val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(imageBytes))
        val directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val orientation = directory?.getInt(ExifIFD0Directory.TAG_ORIENTATION) ?: 1

        val transform = when (orientation) {
            6 -> AffineTransform().apply { rotate(Math.toRadians(90.0), image.width / 2.0, image.height / 2.0) }
            3 -> AffineTransform().apply { rotate(Math.toRadians(180.0), image.width / 2.0, image.height / 2.0) }
            8 -> AffineTransform().apply { rotate(Math.toRadians(270.0), image.width / 2.0, image.height / 2.0) }
            else -> null
        }

        return if (transform != null) {
            val op = AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR)
            val rotatedImage = BufferedImage(image.height, image.width, image.type)
            op.filter(image, rotatedImage)
            rotatedImage
        } else {
            image
        }
    }

    fun uploadImages(files: List<MultipartFile>, folderName: String): List<String> {
        if (files.isEmpty()) {
            throw CommonException("S3-001 업로드할 이미지가 존재하지 않습니다.", HttpStatus.BAD_REQUEST)
        }

        return files.map { file ->
            try {
                val imageBytes = file.inputStream.use { it.readBytes() }
                val fileType = detectFileType(imageBytes)

                if (isUnsupportedFormat(fileType)) {
                    return@map uploadRawImageToS3(imageBytes, folderName, fileType)
                }

                val processedImage = processImage(imageBytes, fileType, file.size)
                uploadFileToS3(processedImage, folderName, "jpg")
            } catch (e: Exception) {
                throw CommonException("S3-002 이미지 업로드 중 오류가 발생했습니다. ${e.message}", HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
    }

    fun uploadImagesOrigin(files : List<MultipartFile>, folderName : String) : List<String> {

        if (files.isEmpty()) {
            log.error("S3-001 : 업로드하려는 파일이 비었습니다.")
            throw CommonException("S3-001", HttpStatus.BAD_REQUEST)
        }

        var result : MutableList<String> = mutableListOf() // 결과값 저장 (링크 주소)

        for (file in files) {

            try {

                val baos = ByteArrayOutputStream()
                IOUtils.copy(file.inputStream, baos)

                val bytes: ByteArray = baos.toByteArray()

                // 인풋 스트림 복사
                var bais = ByteArrayInputStream(bytes)
                var extension = FileTypeDetector.detectFileType(bais) // 사용완료

                if (extension == FileType.Heif || extension == FileType.WebP) {

                    var thumbnailName: String = UUID.randomUUID().toString().plus(".").plus(extension.commonExtension)
                    var fileLocation: String = fileLocation(folderName, thumbnailName)
                    var uploadMetaData : ObjectMetadata = ObjectMetadata()

                    uploadMetaData.contentLength = file.size
                    uploadMetaData.contentType = extension.mimeType

                    try {
                        ncp.assetNcpS3Client().putObject(
                                PutObjectRequest(
                                        bucketName,
                                        fileLocation,
                                        file.inputStream,
                                        uploadMetaData
                                )
                        )
                    } catch (e: Exception) {
                        log.error("S3-004 : S3 업로드 실패 $folderName : $e")
                        throw CommonException("S3-004", HttpStatus.BAD_REQUEST)
                    }

                    result.add(fileLocation)

                    baos.close()
                    bais.close()

                    log.info("이미지 업로드 성공 [ s3 디렉토리 이름 : $folderName ]")

                    continue
                }

                var bufferedImage : BufferedImage = ImageIO.read(bais)
                bais = ByteArrayInputStream(bytes) // reset

                val metaData = ImageMetadataReader.readMetadata(bais)
                var directory = metaData.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                var jpegDirectory = metaData.getFirstDirectoryOfType(JpegDirectory::class.java)

                var width : Double = 0.0
                var height : Double = 0.0

                var newWidth = 0.0 // 픽셀 조정용 값
                var newHeight = 0.0 // 픽셀 조정용 값

                if (jpegDirectory == null) {
                    width = bufferedImage.width.toDouble()
                    height = bufferedImage.height.toDouble()
                }
                else {
                    width = jpegDirectory.imageWidth.toDouble()
                    height = jpegDirectory.imageHeight.toDouble()
                }

                if (bufferedImage.colorModel.hasAlpha()) {

                    //log.info("이미지 알파채널 발견 : 삭제 [folderName : $folderName]")

                    var target = BufferedImage(width.toInt(), height.toInt(), BufferedImage.TYPE_INT_RGB)
                    var targetG : Graphics2D = target.createGraphics()

                    targetG.fillRect(0,0, width.toInt(), height.toInt())
                    targetG.drawImage(bufferedImage, 0,0, null)
                    targetG.dispose()

                    bufferedImage = target

                    //log.info("이미지 알파채널 발견 : 삭제 성공 [folderName : $folderName]")
                }

                var orientation = try {
                    directory.getInt(ExifIFD0Directory.TAG_ORIENTATION)
                } catch (e : Exception) {
                    1
                }

                if (width * height > 12000000) {
                    if(width > height){
                        newWidth = 3464.0
                        newHeight = (height * 3464) / width
                    }
                    else{
                        newHeight = 3464.0
                        newWidth = (width * 3464) / height
                    }
                }
                else {
                    newWidth = width
                    newHeight = height
                }

                var imageType = if (bufferedImage.type == 0) 5 else bufferedImage.type
                var outBufferedImage = BufferedImage(newWidth.toInt(), newHeight.toInt(), imageType)
                var graphics : Graphics2D = outBufferedImage.createGraphics()

                graphics.drawImage(bufferedImage, 0,0, newWidth.toInt(), newHeight.toInt(), null)
                graphics.dispose()

                var newFile = File.createTempFile(UUID.randomUUID().toString().plus(".").plus(extension.commonExtension), "")
                ImageIO.write(outBufferedImage, extension.commonExtension, newFile)

                val atf = AffineTransform()
                when (orientation) {
                    1 -> {}
                    2 -> {
                        atf.scale(-1.0, 1.0)
                        atf.translate(-newWidth, 0.0)
                    }
                    3 -> {
                        atf.translate(newWidth, newHeight)
                        atf.rotate(Math.PI)
                    }
                    4 -> {
                        atf.scale(1.0, -1.0)
                        atf.translate(0.0, -newHeight)
                    }
                    5 -> {
                        atf.rotate(-Math.PI / 2)
                        atf.scale(-1.0, 1.0)
                    }
                    6 -> {
                        atf.translate(newHeight, 0.0)
                        atf.rotate(Math.PI / 2)
                    }
                    7 -> {
                        atf.scale(-1.0, 1.0)
                        atf.translate(-newHeight, 0.0)
                        atf.translate(0.0, newWidth)
                        atf.rotate(3 * Math.PI / 2)
                    }
                    8 -> {
                        atf.translate(0.0, newWidth)
                        atf.rotate(3 * Math.PI / 2)
                    }
                }

                when (orientation) {
                    5, 6, 7, 8 -> {
                        var temp = newWidth
                        newWidth = newHeight
                        newHeight = temp
                    }
                }

                val image: BufferedImage = ImageIO.read(newFile)
                var imageType02 = if (image.type == 0) 5 else image.type
                val afterImage = BufferedImage(newWidth.toInt(), newHeight.toInt(), imageType02)
                val rotateOp = AffineTransformOp(atf, AffineTransformOp.TYPE_BILINEAR)
                val rotatedImage = rotateOp.filter(image, afterImage)
                val iter = ImageIO.getImageWritersByFormatName("jpg")
                val writer = iter.next()
                val iwp = writer.defaultWriteParam

                iwp.compressionMode = ImageWriteParam.MODE_EXPLICIT
                iwp.compressionQuality = calculateImageCompressionRatio(file.size)

                val upload = File.createTempFile("compress_image.jpg", "")
                val fios = FileImageOutputStream(upload)

                writer.output = fios
                writer.write(null, IIOImage(rotatedImage, null, null), iwp)
                fios.close()
                writer.dispose()

                var thumbnailName: String = UUID.randomUUID().toString().plus(".").plus("jpg")
                var fileLocation: String = fileLocation(folderName, thumbnailName)
                var uploadMetaData = ObjectMetadata()

                uploadMetaData.contentLength = upload.length()
                uploadMetaData.contentType = extension.mimeType

                try {
                    ncp.assetNcpS3Client().putObject(
                            PutObjectRequest(
                                    bucketName,
                                    fileLocation,
                                    upload!!.inputStream(),
                                    uploadMetaData
                            )
                    )
                } catch (e: Exception) {
                    log.error("S3-004 : S3 업로드 실패 $folderName : $e")
                    throw CommonException("S3-004", HttpStatus.BAD_REQUEST)
                }

                result.add(fileLocation)

                baos.close()
                bais.close()

                newFile.delete()
                upload.delete()

            } catch (e : Exception) {
                log.error("S3-005 : 이미지 전처리 실패 $folderName : ${file.name} : $e")
                throw CommonException("S3-005", HttpStatus.BAD_REQUEST)
            }
        }

        log.info("이미지 업로드 성공 [ s3 디렉토리 이름 : $folderName ]")

        return result
    }

    fun uploadFiles(files: List<MultipartFile>, folderName: String): List<String> {

        if (files.isEmpty()) {
            log.error("S3-000 : 업로드하려는 파일이 비었습니다.")
            throw CommonException("업로드하려는 파일이 비었습니다", HttpStatus.BAD_REQUEST)
        }

        val result: MutableList<String> = mutableListOf() // 결과값 저장 (링크 주소)

        for (file in files) {

            try {
                // 파일 크기 검증
                if (file.size > 10 * 1024 * 1024) { // 10MB 초과 시 예외 발생
                    log.error("S3-000 : 파일 크기 초과 [ 파일 크기 : ${file.size} ]")
                    throw CommonException("파일 크기 초과", HttpStatus.BAD_REQUEST)
                }

                // 파일 내용 기반 확장자 검증
                val fileBytes = file.inputStream.readBytes()
                val detectedExtension = FileTypeDetector.detectFileType(ByteArrayInputStream(fileBytes))
                val originalFileName = file.originalFilename

                val isPdf = originalFileName?.lowercase()?.endsWith(".pdf")

                if (isPdf != true) {
                    // 허용 가능한 확장자와 MIME 타입
                    val allowedExtensions = mapOf(
                        "gif" to "image/gif",
                        "jpg" to "image/jpeg",
                        "jpeg" to "image/jpeg",
                        "png" to "image/png",
                    )
                    // "pdf" to "application/pdf" // pdf는 detectedExtension가 Unknown으로 나와서 따로 검사함

                    if (!allowedExtensions.containsKey(detectedExtension.commonExtension) ||
                        allowedExtensions[detectedExtension.commonExtension] != detectedExtension.mimeType
                    ) {
                        log.error("S3-000 : 파일 형식 불일치 [ 파일 형식 : ${detectedExtension.commonExtension}, MIME : ${detectedExtension.mimeType} ]")
                        throw CommonException("파일 형식 불일치", HttpStatus.BAD_REQUEST)
                    }
                }

                val fileName: String = if (isPdf == true) {
                    file.originalFilename ?: ""
                }
                else {
                    detectedExtension.commonExtension
                }
                val uuid = UUID.randomUUID().toString()
                val fileLocation: String = folderName.plus("/").plus(uuid).plus("/").plus(fileName)
                val uploadMetaData = ObjectMetadata()

                uploadMetaData.contentLength = file.size
                uploadMetaData.contentType = detectedExtension.mimeType

                try {
                    ncp.assetNcpS3Client().putObject(
                        PutObjectRequest(
                            bucketName,
                            fileLocation,
                            ByteArrayInputStream(fileBytes),
                            uploadMetaData
                        )
                    )
                } catch (e: Exception) {
                    log.error("S3-000 : S3 업로드 실패 $folderName : $e")
                    throw CommonException("S3-004", HttpStatus.BAD_REQUEST)
                }

                result.add(fileLocation)

                log.info("파일 업로드 성공 [ S3 디렉토리 이름 : $folderName ]")

            } catch (e: Exception) {
                log.error("S3-005 : 파일 처리 실패 $folderName : ${file.name} : $e")
                throw CommonException("S3-005", HttpStatus.BAD_REQUEST)
            }
        }

        log.info("파일 업로드 완료 [ S3 디렉토리 이름 : $folderName ]")
        return result
    }


    fun listImages(prefix: String): MutableList<String> {
        try {
            val imagesURL = mutableListOf<String>()

            val listObjectsRequest = ListObjectsRequest()
                .withBucketName(bucketName)
                .withPrefix(prefix)
                .withDelimiter("")
                .withMaxKeys(300)
            val objectListing: ObjectListing = ncp.assetNcpS3Client().listObjects(listObjectsRequest)

            for (objectSummary in objectListing.objectSummaries) {
                imagesURL.add(objectSummary.key)
            }

            return imagesURL

        } catch (e: AmazonS3Exception) {
            e.printStackTrace()
            log.error("S3-004 : S3 AmazonS3Exception : $e")
            throw CommonException("S3-004", HttpStatus.BAD_REQUEST)
        } catch (e: SdkClientException) {
            e.printStackTrace()
            log.error("S3-004 : S3 SdkClientException : $e")
            throw CommonException("S3-004", HttpStatus.BAD_REQUEST)
        }
    }

    fun removeImages(imageUrls: List<String>) {
        try {
            for (imageUrl in imageUrls) {
                ncp.assetNcpS3Client().deleteObject(bucketName, imageUrl)
                log.info("이미지 제거 :  $imageUrl")
            }
        } catch (e: AmazonS3Exception) {
            log.error("S3-004 : S3  AmazonS3Exception : $e")
        } catch (e: SdkClientException) {
            log.error("S3-004 : S3 sdkClientException : $e")
        } catch (e : Exception) {
            log.error("S3-004 : $e")
        }
    }

    fun copyImages(prev : List<String>, next : List<String>) {

        if (prev.isNullOrEmpty() || next.isNullOrEmpty()) {
            return
        }
        else if (prev.size != next.size) {
            return
        }

        try {
            for (index : Int in prev.indices) {
                ncp.assetNcpS3Client().copyObject(bucketName, prev[index], bucketName, next[index])
            }

        } catch (e : Exception) {
            return
        }

        log.info("NCP 이미지 복사 저장 완료 [prev : $prev, next : $next]")
    }

    fun uploadVideo(folderName : String, file : MultipartFile) : ChatVideoResponseDTO {

        val baos = ByteArrayOutputStream() // 새 스트림
        IOUtils.copy(file.inputStream, baos) // 스트림 복사

        val bytes : ByteArray = baos.toByteArray()

        // 인풋 스트림 복사
        var bais = ByteArrayInputStream(bytes)
        var extension = FileTypeDetector.detectFileType(bais) // 파일 타입 추출 완료 // bais 사용 불가
        var fileName = UUID.randomUUID().toString()

        log.info("동영상 파일 정보 체크 - [확장자 : ${extension}, 파일 크기 : ${file.size}]")

        var thumbnailFile  = File.createTempFile("$fileName.png", "") // 비어있음 -> 이미지 파일
        var tempFile = File.createTempFile(file.name, "") // 비어있음 -> 동영상 파일
        var fos = FileOutputStream(tempFile)

        fos.write(bytes) // bytes 사용불가
        fos.close() // 파일 종결 후 파일 완성

        val frameGrab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(tempFile))
        var duration = frameGrab.videoTrack.meta.totalDuration

        frameGrab.seekToSecondPrecise(0.0) // 프레임 단위로 0.0초에 잘랐음

        val picture : Picture = frameGrab.nativeFrame
        val bi = AWTUtil.toBufferedImage(picture) // 이미지 파일

        ImageIO.write(bi, "png", thumbnailFile) // 썸네일 파일 사용 가능

        var videoName = fileName.plus(".").plus(extension.name.lowercase())
        var videoFileLocation = fileLocation(folderName, videoName)
        var thumbnailName: String = fileName.plus(".png")
        var thumbnailFileLocation: String = fileLocation(folderName, thumbnailName)

        try {
            var uploadMetaData = ObjectMetadata()

            uploadMetaData.contentLength = tempFile.length()
            uploadMetaData.contentType = extension.mimeType

            s3.assetS3Client().putObject(
                PutObjectRequest(
                    videoBucketName,
                    videoFileLocation,
                    tempFile.inputStream(),
                    uploadMetaData
                )
            )
        } catch (e : Exception) {
            log.error("동영상 파일 전송 오류 : $e")
            throw CommonException("동영상 파일 전송 오류", HttpStatus.BAD_REQUEST)
        }

        //
        try {
            var uploadMetaData = ObjectMetadata()

            uploadMetaData.contentLength = thumbnailFile.length()
            uploadMetaData.contentType = "image/png"

            s3.assetS3Client().putObject(
                PutObjectRequest(
                    videoBucketName,
                    thumbnailFileLocation,
                    thumbnailFile.inputStream(),
                    uploadMetaData
                )
            )
        } catch (e : Exception) {
            log.error("썸네일 파일 전송 오류 : $e")
            throw CommonException("이미지 파일 전송 오류", HttpStatus.BAD_REQUEST)
        }

        return ChatVideoResponseDTO(
            url = videoFileLocation,
            thumbnail = thumbnailFileLocation,
            duration = duration.toInt()
        )
    }

    companion object {
        private val log: Logger = LogManager.getLogger(this.javaClass.name)
    }
}


