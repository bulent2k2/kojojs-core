package scalafiddle.router

import java.nio.file.Paths

import akka.actor.ActorSystem
import org.slf4j.LoggerFactory

import scalafiddle.router.cache.FileCache

object Server extends App {
  private val log = LoggerFactory.getLogger(getClass)

  // SESSİZ ÖNTANIMLIYI GÜRÜLTÜLÜ YAP (inceleme önerisi, kojojs-core#32).
  // reference.conf'taki `secret = "secret"` çalışıyor ama hiçbir şey korumuyor:
  // değer yukarı akış ScalaFiddle deposunda herkese açık. Koruduğu iki kapı:
  //   /durum    -- derleyici sayıları
  //   /compiler -- bir compilerServer'ı KAYDEDEN WebSocket; kaydolan derleyici
  //                kullanıcının kaynak kodunu alıyor ve döndürdüğü JavaScript
  //                kullanıcının tarayıcısında koşuyor
  // Yani öntanımlıyla koşan bir kurulumda o kapıya erişebilen biri çocuğun
  // tarayıcısında kendi JS'ini koşturabilir. Bugün bunu engelleyen tek şey
  // BAŞKA BİR DEPODAKİ nginx beyaz listesi (koco-deploy) -- router'ın haberi
  // bile yok.
  //
  // Başlamayı REDDETMİYORUZ bilerek: yerel geliştirme ve sınamalar ortam
  // değişkeni vermeden koşuyor, onları kırmak faydadan çok zarar. Ama satır
  // ERROR ve artık konsola da düşüyor (#31), yani yanlış yapılandırma sessiz
  // bir açıklık değil, günlükte duran bir satır.
  if (Config.secret == "secret") {
    log.error(
      "SCALAFIDDLE_SECRET is not set: the router is running with the public default secret. " +
        "The /compiler registration gate and /durum are effectively unprotected.")
  }

  val system = ActorSystem()


  val cache           = new FileCache(Paths.get(Config.cacheDir))
  val compilerManager = system.actorOf(CompilerManager.props, "CompilerManager")

  val webService = new WebService(system, cache, compilerManager)
}
