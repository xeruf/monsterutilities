@file:Suppress("BlockingMethodInNonBlockingContext")

package xerus.monstercat.api

import io.kotlintest.matchers.collections.shouldNotContain
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import xerus.monstercat.Sheets
import xerus.monstercat.api.response.ArtistRel
import xerus.monstercat.api.response.FullArtist
import xerus.monstercat.api.response.ListResponse

internal class APIUtilsTest: StringSpec({
	
	"find Edge of the World by Karma Fields" {
		val edge = APIUtils.find("Edge Of The World", "Karma Fields")!!
		edge.artists shouldNotContain ArtistRel("Razihel")
		edge.artistsTitle shouldBe "Karma Fields"
	}
	
	"get Artist Exceptions" {
		val artistsJson = APIUtilsTest::class.java.getResourceAsStream("artists.json")
		artistsJson.shouldNotBeNull()
		val artists = Sheets.JSON_FACTORY.fromInputStream(artistsJson, ArtistResponse::class.java)
		artists.total shouldBe 465
		APIUtils.getArtistExceptions(artists.results) shouldBe arrayOf("Case & Point", "Gent & Jawns", "A.M.C & Turno", "Gamble & Burke")
	}
	
})

class ArtistResponse: ListResponse<FullArtist>()