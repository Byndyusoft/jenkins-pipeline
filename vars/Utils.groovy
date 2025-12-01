/** Additional functions for pipeline work */
class Utils {
    /** Merge two maps */
    Map merge(Map lhs, Map rhs) {
        if (!lhs.isEmpty() && !rhs.isEmpty()) {
            return rhs.inject(lhs.clone()) { map, entry ->
                String key = entry.key

                if (map[key] instanceof Map && entry.value instanceof Map) {
                    map[key] = merge(map[key] as Map, entry.value as Map)
                } else {
                    map[key] = entry.value
                }
                return map
            } as Map
        } else {
            if (!lhs.isEmpty()) {
                return lhs
            }

            if (!rhs.isEmpty()) {
                return rhs
            }

            return [:]
        }
    }

    /** Convert list to string */
    String listToString(Map value) {
        String valueString = value.collect { k, v -> "$k=$v" }.join(' ')

        return valueString
    }

    /** Change the string for k8s */
    String prepareName(String value) {
        if (value == '') {
            return ''
        }

        String processedValue = value.replace('_', '-').replace('/', '-')

        if (processedValue.length() > 53) {
            processedValue = processedValue.reverse().take(53).reverse()
        }

        if (processedValue.startsWith('-')) {
            processedValue = processedValue.substring(1, processedValue.length())
        }

        if (processedValue.endsWith('-')) {
            processedValue = processedValue.substring(0, processedValue.length() - 1)
        }

        return processedValue
    }

    /** Convert arbitrary YAML value to list of non-empty strings */
    static List<String> toStringList(Object value) {
        if (value == null) {
            return []
        }
        if (value instanceof List) {
            return value.collect { it?.toString() ?: '' }.findAll { it }.unique()
        }
        if (value instanceof String) {
            return value.split(',').collect { it.trim() }.findAll { it }.unique()
        }
        return [value.toString()].findAll { it }.unique()
    }

    /** Convert list to Jenkins reactive choice format */
    static String toJenkinsChoiceFormat(List<String> items) {
        if (!items) {
            return ''
        }
        return items.collect { env ->
            def safe = (env ?: '').toString().trim()
            safe = safe.replace("'", "\\'")
            return "'${safe}'"
        }.join(',')
    }
}
