/** Message logger, outputs messages to output */
void logDebug(String message) {
    if (env.DEBUG == 'true') {
        log(message, 'debug')
    }
}

void logInfo(String message) {
    log(message, 'info')
}

void logError(String message) {
    log(message, 'error')
}

private void log(String message, String level) {
    def datetime = new Date()
    println("""${datetime} | [${level}]: ${message}""")
}
