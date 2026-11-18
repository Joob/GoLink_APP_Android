package co.golink.tester.domain.files

/**
 * Extensão de um nome de ficheiro — "README", ".env" e "file." não têm.
 * Mesma regra que extOf() na web e file_extension_from_name() no servidor.
 */
fun extOf(name: String?): String {
    val s = name.orEmpty()
    val dot = s.lastIndexOf('.')
    return if (dot > 0 && dot < s.lastIndex) s.substring(dot + 1) else ""
}

/** Igual ao safe_extension() do servidor: minúsculas, só [a-z0-9]. */
private fun normExt(value: String?): String =
    value.orEmpty().lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

/**
 * Extensão guardada no `mimetype` (é a extensão enviada no upload). Vazia
 * quando é o próprio nome: uploads antigos de ficheiros sem extensão
 * guardaram lá o nome inteiro ("README").
 */
fun storedExt(name: String?, mimetype: String?): String {
    val m = mimetype?.trim().orEmpty()
    return if (m.isNotEmpty() && normExt(m) != normExt(name)) m else ""
}
