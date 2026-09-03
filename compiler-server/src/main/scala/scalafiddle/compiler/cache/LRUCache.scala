package scalafiddle.compiler.cache

import scalafiddle.compiler.Config
import scalafiddle.shared.ExtLib
import org.slf4j.LoggerFactory

import scala.collection.mutable

class LRUCache[T](name: String) {
  protected val cacheSize = Config.compilerCacheSize
  private val cache       = mutable.ListMap.empty[Int, T]
  private val log         = LoggerFactory.getLogger(getClass)

  def getOrUpdate(libs: Set[ExtLib], update: => T): T = {
    val hash = hashLibs(libs)
    cache.get(hash) match {
      case Some(value) =>
        log.debug(s"Cache hit for $name")
        cache += hash -> value
        value
      case None =>
        log.debug(s"Cache miss for $name")
        val value = update
        if (cache.size >= cacheSize) {
          cache.iterator.drop(cacheSize - 1).foreach(cache -= _._1)
        }
        cache += hash -> value
        value
    }
  }

  def hashLibs(libs: Set[ExtLib]): Int = {
    libs.foldLeft(0)(_ ^ _.hashCode())
  }

  def remove(libs: Set[ExtLib]): Unit = {
    log.debug(s"Removing from cache: $name")
    val hash = hashLibs(libs)
    cache.remove(hash)
  }

  def clear(): Unit = {
    log.debug(s"Clearing cache: $name")
    cache.clear()
  }
}
