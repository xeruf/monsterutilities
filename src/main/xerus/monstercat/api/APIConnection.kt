@file:Suppress("DEPRECATION")

package xerus.monstercat.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.apache.http.HttpResponse
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.impl.cookie.BasicClientCookie
import org.apache.http.pool.PoolStats
import xerus.ktutil.helpers.HTTPQuery
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.listen
import xerus.monstercat.Sheets
import xerus.monstercat.api.response.*
import xerus.monstercat.downloader.CONNECTSID
import xerus.monstercat.downloader.QUALITY
import java.io.IOException
import java.io.InputStream
import java.net.URI
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger { }

/** eases query creation to the Monstercat API */
class APIConnection(vararg path: String) : HTTPQuery<APIConnection>() {
	
	private val path: String = "/api/" + path.joinToString("/")
	val uri: URI
		get() = URI("https", "connect.monstercat.com", path, getQuery(), null)
	
	fun fields(clazz: KClass<*>) = addQuery("fields", *clazz.declaredKeys.toTypedArray())
	fun limit(limit: Int) = addQuery("limit", limit.toString())
	
	// Requesting
	
	/** calls [getContent] and uses a [com.google.api.client.json.JsonFactory]
	 * to parse the response onto a new instance of [T]
	 * @return the response parsed onto [T] or null if there was an error */
	fun <T> parseJSON(destination: Class<T>): T? {
		val inputStream = try {
			getContent()
		} catch(e: IOException) {
			return null
		}
		return try {
			Sheets.JSON_FACTORY.fromInputStream(inputStream, destination)
		} catch(e: Exception) {
			logger.warn("Error parsing response of $uri: $e", e)
			null
		}
	}
	
	/** @return null when the connection fails, else the parsed result */
	fun getReleases() =
		parseJSON(ReleaseResponse::class.java)?.results?.map { it.init() }
	
	/** @return null when the connection fails, else the parsed result */
	fun getTracks() =
		parseJSON(TrackResponse::class.java)?.results
	
	/** Aborts this connection and thus terminates the InputStream if active */
	fun abort() {
		httpGet?.abort()
	}
	
	// Direct Requesting
	
	private var httpGet: HttpGet? = null
	fun execute() {
		httpGet = HttpGet(uri)
		response = connectWithCookie(httpGet!!)
	}
	
	private var response: HttpResponse? = null
	fun getResponse(): HttpResponse {
		if(response == null)
			execute()
		return response!!
	}
	
	/**@return the content of the response
	 * @throws IOException when the connection fails */
	fun getContent(): InputStream {
		val resp = getResponse()
		if(!resp.entity.isRepeatable)
			response = null
		return resp.entity.content
	}
	
	override fun toString(): String = "APIConnection(uri=$uri)"
	
	companion object {
		var httpClient = createHttpClient(CONNECTSID())
		
		init {
			CONNECTSID.listen { checkConnectsid(it) }.changed(null, null, CONNECTSID())
		}
		
		fun connectWithCookie(httpGet: HttpGet): CloseableHttpResponse {
			logger.trace { "Connecting to ${httpGet.uri}" }
			return httpClient.execute(httpGet)
		}
		
		fun createHttpClient(connectsid: String) = HttpClientBuilder.create()
			.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).setConnectTimeout(10000).setSocketTimeout(10000).setConnectionRequestTimeout(10000).build())
			.setDefaultCookieStore(BasicClientCookie("connect.sid", connectsid).run {
				domain = "connect.monstercat.com"
				path = "/"
				BasicCookieStore().also { it.addCookie(this) }
			})
			.setConnectionManager(PoolingHttpClientConnectionManager().apply {
				defaultMaxPerRoute = 500
				maxTotal = 500
				if(logger.isTraceEnabled)
					GlobalScope.launch {
						val manager = this@apply
						val name = manager.javaClass.simpleName + "@" + manager.javaClass.hashCode()
						var stats: PoolStats = manager.totalStats
						while(true) {
							val newstats = manager.totalStats
							if(stats.leased != newstats.leased || stats.pending != newstats.pending) {
								stats = this@apply.totalStats
								logger.trace("$name: $stats")
							}
							delay(500)
						}
					}
			})
			.build()
		
		val connectValidity = SimpleObservable(ConnectValidity.NOCONNECTION)
		
		private var lastResult: ConnectResult? = null
		private fun checkConnectsid(connectsid: String) {
			if(lastResult?.connectsid != connectsid) {
				GlobalScope.launch {
					val result = getConnectValidity(connectsid)
					if(QUALITY().isEmpty())
						result.session?.settings?.run {
							QUALITY.set(preferredDownloadFormat)
						}
					lastResult = result.takeUnless { it.validity == ConnectValidity.NOCONNECTION }
					connectValidity.value = result.validity
				}
			}
		}
		
		private fun getConnectValidity(connectsid: String): ConnectResult {
			httpClient = createHttpClient(connectsid)
			val session = APIConnection("self", "session").parseJSON(Session::class.java)
			val validity = when {
				session == null -> ConnectValidity.NOCONNECTION
				session.user == null -> ConnectValidity.NOUSER
				session.user!!.goldService -> {
					Cache.refresh(true)
					ConnectValidity.GOLD
				}
				else -> ConnectValidity.NOGOLD
			}
			return ConnectResult(connectsid, validity, session)
		}
		
		data class ConnectResult(val connectsid: String, val validity: ConnectValidity, val session: Session?)
	}
	
}

enum class ConnectValidity {
	NOUSER, NOGOLD, GOLD, NOCONNECTION
}