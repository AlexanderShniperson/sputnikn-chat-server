package net.orionlab.sputniknchatserver

import net.orionlab.sputniknchatserver.db.BaseDao
import net.orionlab.sputniknchatserver.routing.appModule
import com.zaxxer.hikari.HikariConfig
import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// SSL embedded server
// https://github.com/ktorio/ktor-documentation/tree/main/codeSnippets/snippets/ssl-embedded-server

// To allow netty to access the class, start java with the following option:
// --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
// For Netty to use its direct buffer optimizations, you also need to set
// -Dio.netty.tryReflectionSetAccessible=true


@Suppress("UNUSED_PARAMETER")
fun main(args: Array<String>) {
    val path = BaseDao::class.java.protectionDomain.codeSource.location.path
    val selfJarPath = URLDecoder.decode(path, "UTF-8")
    val serverHostParam = "host"
    val serverPortParam = "port"
    val mediaPathParam = "mediaPath"
    val defaultServerHost = "0.0.0.0"
    val defaultServerPort = 8443
    println("Runtime parameters:")
    println("$serverHostParam - [optional] network interface to listen, default is $defaultServerHost")
    println("$serverPortParam - [optional] port to listen, default is $defaultServerPort")
    println("$mediaPathParam - [required] chat media files directory")
    println("\nUsage example:")
    println("java -jar '$selfJarPath' $serverHostParam=$defaultServerHost $serverPortParam=$defaultServerPort $mediaPathParam=./media\n")
    val serverHost = findParseParam(args, serverHostParam, defaultServerHost)
    val serverPort =
        findParseParam(args, serverPortParam, defaultServerPort.toString()).toIntOrNull() ?: defaultServerPort
    val mediaPath = findParseParam(args, mediaPathParam, "")

    if (mediaPath.isEmpty()) {
        println("Required runtime param is not defined: $mediaPathParam")
        exitProcess(1)
    } else {
        val mediaFile = File(mediaPath)
        mediaFile.mkdirs()
        println("Using mediaPath='${mediaFile.absoluteFile}'")
    }

    val jdbcDriverClassParam = "JDBC_DRIVER"
    val jdbcDriverClass = getSafeEnv(jdbcDriverClassParam)
    val jdbcDatabaseUrlParam = "JDBC_DATABASE_URL"
    val jdbcDatabaseUrl = getSafeEnv(jdbcDatabaseUrlParam)
    val jdbcPoolSize = getSafeEnv("JDBC_POOL_SIZE")?.toIntOrNull() ?: 10

    if (jdbcDriverClass == null || jdbcDatabaseUrl == null) {
        println("WARNING!\nSome required system variables is not defined!")
        if (jdbcDriverClass == null) {
            println("'$jdbcDriverClassParam' is absent!")
        }
        if (jdbcDatabaseUrl == null) {
            println("'$jdbcDatabaseUrlParam' is absent!")
        }
        exitProcess(1)
    }

    val dbConfig = HikariConfig().apply {
        driverClassName = jdbcDriverClass
        jdbcUrl = jdbcDatabaseUrl
        maximumPoolSize = jdbcPoolSize
    }

    val keyStoreFile = File("keystore.jks")
//    val keystore = if (keyStoreFile.exists())
//        KeyStore.getInstance(keyStoreFile, "foobar".toCharArray())
//    else
    val keystore = generateCertificate(
        file = keyStoreFile,
        keyAlias = "sampleAlias",
        keyPassword = "foobar",
        jksPassword = "foobar"
    )

    val environment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
//        sslConnector(
//            keyStore = keystore,
//            keyAlias = "sampleAlias",
//            keyStorePassword = { "foobar".toCharArray() },
//            privateKeyPassword = { "foobar".toCharArray() }) {
//            host = serverHost
//            port = serverPort
//            keyStorePath = keyStoreFile
//        }
        connector {
            host = serverHost
            port = serverPort
        }
        module { appModule(dbConfig, mediaPath) }
    }

    val server = embeddedServer(
        Netty,
        environment = environment
    ).start(true)

    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(3, 5, TimeUnit.SECONDS)
    })

    Thread.currentThread().join()
}

private fun getSafeEnv(envKey: String): String? {
    return try {
        System.getenv(envKey)
    } catch (ex: Throwable) {
        null
    }
}

private fun findParseParam(args: Array<String>, paramName: String, orElse: String): String {
    return args.find { it.startsWith(paramName) }?.let {
        val paramValue = it.removePrefix("$paramName=")
        paramValue.ifEmpty { orElse }
    } ?: orElse
}
