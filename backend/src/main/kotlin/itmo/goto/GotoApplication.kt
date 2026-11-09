package itmo.goto

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GotoApplication

fun main(args: Array<String>) {
	runApplication<GotoApplication>(*args)
}
