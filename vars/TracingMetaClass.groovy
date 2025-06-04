/**
    Metaclass for tracing calls to all methods of a class https://groovy-lang.org/metaprogramming.html#_delegating_metaclass
    The method parameters and execution results are logged. 
*/
class TracingMetaClass extends DelegatingMetaClass {
    private final Logger logger

    TracingMetaClass(MetaClass metaClass, Logger logger) {
        super(metaClass)
        this.logger = logger
    }

    Object invokeMethod(Object object, String methodName, Object[] args) {
        logger.logInfo("enter $theClass.$methodName, args = $args")

        def result = super.invokeMethod(object, methodName, args)

        logger.logInfo("exit $theClass.$methodName, result = $result")

        return result
    }
}
