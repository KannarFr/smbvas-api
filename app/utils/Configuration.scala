package utils

import javax.inject._
import play.api._
import play.api.libs.json.Json
import scala.collection.JavaConverters._

@Singleton
class Configuration @Inject()(
    c: play.api.Configuration
) {
    def getString(key: String, subKey: String): Option[String] = c.getOptional[String](key + "." + subKey)

    case class ResourceRepertory(path: String)
    val resource = ({
        val _key = "resource"
        for {
            path <- getString(_key, "dir")
        } yield {
            ResourceRepertory(path)
        }
    }).get
}