package ui.console

import java.util.Scanner

abstract class ConsoleBaseScreen {

    private val scanner = Scanner(System.`in`)

    protected fun readLine(prompt: String): String {
        print("$prompt: ")
        return scanner.nextLine().trim()
    }

    protected fun readInt(prompt: String): Int? {
        print("$prompt: ")
        return scanner.nextLine().trim().toIntOrNull()
    }

    protected fun printLine(message: String) {
        println(message)
    }

    protected fun printSeparator() {
        println("─".repeat(50))
    }

    protected fun clearScreen() {
        repeat(30) { println() }
    }
}