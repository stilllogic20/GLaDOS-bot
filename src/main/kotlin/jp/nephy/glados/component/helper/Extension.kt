package jp.nephy.glados.component.helper

import com.mongodb.client.MongoCollection
import jp.nephy.glados.GLaDOS
import jp.nephy.jsonkt.JsonKt
import jp.nephy.jsonkt.JsonModel
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.json.JsonWriterSettings
import org.litote.kmongo.findOne
import java.io.File
import java.net.JarURLConnection
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.math.roundToLong


fun Long?.toDeltaMilliSecondString(): String? {
    if (this == null) {
        return null
    }

    return (Date().time - this).toMilliSecondString()
}

private fun Long.divMod(other: Long, action: (q: Long, r: Long) -> Unit) {
    val q = div(other)
    val r = this - other * q
    action(q, r)
}

fun Long?.toMilliSecondString(): String? {
    if (this == null) {
        return null
    }

    var sec = div(1000.0).roundToLong()
    return buildString {
        sec.divMod(60 * 60 * 24) { q, r ->
            if (q > 0) {
                append("${q}日")
            }
            sec = r
        }
        sec.divMod(60 * 60) { q, r ->
            if (q > 0) {
                append("${q}時間")
            }
            sec = r
        }
        sec.divMod(60) { q, r ->
            if (q > 0) {
                append("${q}分")
            }
            sec = r
        }
        append("${sec}秒")
    }
}

val Number.charLength: Int
    get() = toString().length

fun <T> Iterable<T>.sumBy(selector: (T) -> Long): Long {
    return map(selector).sum()
}

fun <T> MutableList<T>.removeAtOrNull(index: Int): T? {
    return try {
        removeAt(index)
    } catch (e: IndexOutOfBoundsException) {
        null
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> enumurateClasses(packageName: String): List<Class<T>> {
    val resourceName = packageName.replace('.', File.separatorChar)
    val resourceName2 = packageName.replace('.', '/')
    val classLoader = Thread.currentThread().contextClassLoader
    val root = classLoader.getResource(resourceName) ?: classLoader.getResource(resourceName2)
    val classNamePattern = "([A-Za-z]+)\\.class".toRegex()

    return when (root.protocol) {
        "file" -> {
            val result = mutableSetOf<Class<T>>()

            Files.walkFileTree(Paths.get(root.toURI()), object: SimpleFileVisitor<Path>() {
                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val fileName = path.fileName.toString()
                    if (classNamePattern.containsMatchIn(fileName)) {
                        val className = "$packageName${path.toString().split(resourceName).last().replace(File.separatorChar, '.').removeSuffix(".class")}"
                        val klass = Class.forName(className)
                        if (klass.superclass == T::class.java) {
                            result.add(klass as Class<T>)
                        }
                    }

                    return FileVisitResult.CONTINUE
                }
            })
            result
        }
        "jar" -> {
            (root.openConnection() as JarURLConnection).jarFile.use {
                it.entries().toList()
                        .filter { classNamePattern.containsMatchIn(it.name) && it.name.startsWith(resourceName2) }
                        .map { "$packageName${it.name.split(resourceName2).last().replace('/', '.').removeSuffix(".class")}" }
                        .map { classLoader.loadClass(it) }
                        .filter { it.superclass == T::class.java }
                        .map { it as Class<T> }
                        .toSet()
            }
        }
        else -> emptySet()
    }.sortedBy { it.canonicalName }
}

fun <T> List<T>.joinToStringIndexed(separator: String, operation: (Int, T) -> String): String {
    return mapIndexed { index, t -> operation(index, t) }.joinToString(separator)
}

fun tmpFile(first: String, vararg more: String): File {
    val tmpDir = Paths.get(if (GLaDOS.instance.isDebugMode) "tmp_debug" else "tmp")
    if (! Files.exists(tmpDir)) {
        Files.createDirectory(tmpDir)
    }

    if (more.isNotEmpty()) {
        Files.createDirectories(Paths.get(tmpDir.toString(), first, *more.dropLast(1).toTypedArray()))
    }

    return Paths.get(tmpDir.toString(), first, *more).toFile()
}

val pureJsonWriter = JsonWriterSettings.builder().int64Converter { value, writer ->
    writer.writeRaw(value.toString())
}.build()!!

inline fun <reified T: JsonModel> MongoCollection<Document>.findAndParse(filter: Bson? = null): List<T> {
    return if (filter != null) {
        find(filter)
    } else {
        find()
    }.toList().map { JsonKt.parse<T>(it.toJson(pureJsonWriter)) }
}

inline fun <reified T: JsonModel> MongoCollection<Document>.findOneAndParse(filter: Bson? = null): T? {
    return if (filter != null) {
        findOne(filter)
    } else {
        findOne()
    }?.let {
        JsonKt.parse(it.toJson(pureJsonWriter))
    }
}
