package com.github.hyuno.spring_playground

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringPlaygroundApplication

fun main(args: Array<String>) {
	runApplication<SpringPlaygroundApplication>(*args)
}
