package scalafiddle.router

import akka.actor._
import scalafiddle.shared._
import upickle.default._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class RegisterCompiler(id: String, compilerService: ActorRef, scalaVersion: String)

case class UnregisterCompiler(id: String)

case class UpdateState(id: String, newState: CompilerState)

case class CancelCompilation(id: String)

case class CancelCompletion(id: String)

case object RefreshLibraries

case object CheckCompilers

// Retire yollanan derleyicinin CompilerService'ini, derleyici kendi çıkıp
// bağlantıyı kapatmadıysa (Retire'ı tanımayan eski bir derleyici) bir süre
// sonra durdurur. Olağan yolda iş yapmaz: çıkan sürecin ws'i kapanınca
// ActorFlow servisi zaten durdurmuş olur.
case class DropCompiler(compilerService: ActorRef)

// Dışarıdan durum sorgusu (WebService'in /durum ucu). Yalnız okur, hiçbir şeyi
// değiştirmez. Alanlar İNGİLİZCE: bu uç işletmeciye bakıyor ve kodun kendi
// sözlüğünü (CompilerState.Ready/Compiling/Initializing) yansıtması, günlük
// satırlarıyla eşleştirilebilmesi için önemli -- kullanıcıya çıkan metinlerin
// Türkçe olması kuralı buraya uygulanmıyor.
case object GetStatus

case class CompilerStatus(id: String,
                          scalaVersion: String,
                          state: String,
                          lastActivitySeconds: Long,
                          lastSeenSeconds: Long,
                          served: Int)

object CompilerStatus { implicit val rw: ReadWriter[CompilerStatus] = macroRW }

case class RouterStatus(registered: Int,
                        ready: Int,
                        compiling: Int,
                        initializing: Int,
                        queued: Int,
                        pending: Int,
                        restarting: Int,
                        compilers: Seq[CompilerStatus])

object RouterStatus { implicit val rw: ReadWriter[RouterStatus] = macroRW }

class CompilerManager extends Actor with ActorLogging {
  import CompilerManager._

  val compilers          = mutable.Map.empty[String, CompilerInfo]
  var compilerQueue      = mutable.Queue.empty[(CompilerRequest, ActorRef)]
  val compilationPending = mutable.Map.empty[String, ActorRef]
  var currentLibs        = Map.empty[String, Seq[ExtLib]]
  var compilerTimer = context.system.scheduler
    .schedule(Config.compilerHealth.checkInitialDelay, Config.compilerHealth.checkInterval, context.self, CheckCompilers)
  // Router'ın BİLEREK kapattığı (takılma ya da yenilenme) ve yerine yenisi
  // beklenen derleyiciler: id -> bekleme süresinin bittiği an. Yeni bir kayıt
  // en eski girdiyi siler; süresi dolan girdi kendiliğinden düşer. Tek işlevi
  // /saglik'e "bu eksik planlı, birazdan dolacak" demek (restarting).
  val retiring           = mutable.Map.empty[String, Long]
  val recycleAfter       = Config.compilerHealth.recycleAfter
  // > 0: kurulum çıkan derleyiciyi yeniden başlatıyor (reference.conf).
  val recycling          = recycleAfter > 0
  val dependencyRE       = """ *// \$FiddleDependency (.+)""".r
  val scalaVersionRE     = """ *// \$ScalaVersion (.+)""".r
  val defaultLibs =
    Config.defaultLibs.mapValues(_.map(lib => s"// $$FiddleDependency $lib").mkString("\n", "\n", "\n"))

  def now = System.currentTimeMillis()

  override def preStart(): Unit = {
    super.preStart()
    // try to load libraries
    currentLibs = loadLibraries(Config.extLibs, Config.defaultLibs)
    if (currentLibs.isEmpty) {
      // schedule a periodic library update
      context.system.scheduler.scheduleOnce(5.seconds, context.self, RefreshLibraries)
    }
  }

  override def postStop(): Unit = {
    compilerTimer.cancel()
    super.postStop()
  }

  def loadLibraries(libUris: Map[String, String], defaultLibs: Map[String, Seq[String]]): Map[String, Seq[ExtLib]] = {
    Config.scalaVersions.map { version =>
      version -> ((libUris.get(version), defaultLibs.get(version)) match {
        case (Some(uri), Some(versionLibs)) =>
          try {
            log.debug(s"Loading libraries from $uri")
            val data = if (uri.startsWith("file:")) {
              // load from file system
              scala.io.Source.fromFile(uri.drop(5), "UTF-8").mkString
            } else if (uri.startsWith("http")) {
              // load from internet
              System.setProperty(
                "http.agent",
                "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36")
              scala.io.Source.fromURL(uri, "UTF-8").mkString
            } else {
              // load from resources
              scala.io.Source.fromInputStream(getClass.getResourceAsStream(uri), "UTF-8").mkString
            }
            val extLibs = read[Seq[String]](data)
            (extLibs ++ versionLibs).map(ExtLib(_))
          } catch {
            case e: Throwable =>
              log.error(e, s"Unable to load libraries")
              Nil
          }
        case _ =>
          Nil
      })
    }.toMap
  }

  def extractLibs(source: String): (Set[ExtLib], Option[String]) = {
    val codeLines = source.replaceAll("\r", "").split('\n')
    val libs = codeLines.collect {
      case dependencyRE(dep) => ExtLib(dep)
    }.toSet
    val version = codeLines.collect {
      case scalaVersionRE(v) => v
    }.headOption
    (libs, version)
  }

  def selectCompiler(req: CompilerRequest): Option[CompilerInfo] = {
    // extract libs from the source
    log.debug(s"Source\n${req.source}")
    val (libs, scalaVersionOpt) = extractLibs(req.source)
    // eski kayıtlı fiddle'lar "// $ScalaVersion 2.12" taşıyor; 2.13 geçişinden
    // (Faz 3) sonra 2.12 derleyicisi yok -- eski bağlantılar kırılmasın diye
    // 2.13'e eşleniyor (Kojo API'si kaynak uyumlu)
    val scalaVersion = scalaVersionOpt.getOrElse("2.13") match {
      case "2.12" => "2.13"
      case v      => v
    }

    log.debug(s"Selecting compiler for Scala $scalaVersion and libs $libs")
    // check that all libs are supported
    val versionLibs = currentLibs.getOrElse(scalaVersion, Vector.empty)
    // log.debug(s"Libraries:\n$versionLibs")
    libs.foreach(lib => if (!versionLibs.contains(lib)) throw new IllegalArgumentException(s"Library $lib is not supported"))
    // select the best available compiler server based on:
    // 1) time of last activity
    // 2) set of libraries
    compilers.values.toSeq
      .filter(c => c.state == CompilerState.Ready && c.scalaVersion == scalaVersion)
      .sortBy(_.lastActivity)
      .zipWithIndex
      .sortBy(info => if (info._1.lastLibs == libs) -1 else info._2) // use index to ensure stable sort
      .headOption
      .map(_._1.copy(lastLibs = libs))
  }

  def updateCompilerState(id: String, newState: CompilerState): Unit = {
    if (compilers.contains(id)) {
      compilers.update(id, compilers(id).copy(state = newState, lastActivity = now))
    }
  }

  def compilerSeen(id: String): Unit = {
    if (compilers.contains(id)) {
      compilers.update(id, compilers(id).copy(lastSeen = now))
    }
  }

  def purgeRetiring(): Unit = {
    val t = now
    retiring.filterInPlace { case (_, deadline) => deadline > t }
  }

  /**
   * Derleyiciyi havuzdan çıkarır ve yerine yenisini bekler. Gözetmen varsa
   * (recycling) Retire yollanır: süreç biter, gözetmen TAZE bir JVM başlatır.
   * Yoksa yalnız bağlantı kesilir: Retire'ı kimsenin karşılamayacağı bir
   * kurulumda derleyiciyi kalıcı olarak kaybetmektense aynı JVM'in yeniden
   * bağlanması yeğ (yukarı akışın 120 sn ping kuralının yaptığı da bu).
   */
  def retire(info: CompilerInfo): Unit = {
    compilers -= info.id
    context.unwatch(info.compilerService)
    retiring += info.id -> (now + Config.compilerHealth.restartGrace.toMillis)
    if (recycling) {
      info.compilerService ! Retire
      context.system.scheduler.scheduleOnce(retireGrace, context.self, DropCompiler(info.compilerService))
    } else {
      context.stop(info.compilerService)
    }
  }

  /**
   * Yenilenme HİÇBİR ZAMAN kapasiteyi sıfırlamasın: aynı anda en çok bir
   * derleyici yenileniyor, ve geride çalışır (Initializing olmayan) en az
   * bir derleyici kalıyorsa. Koşul tutmazsa sıra bir sonraki cevaba kalır.
   */
  def canRecycle(info: CompilerInfo): Boolean =
    retiring.isEmpty && compilers.values.exists(c => c.id != info.id && c.state != CompilerState.Initializing)

  def processQueue(): Unit = {
    if (compilerQueue.nonEmpty) {
      val (req, sourceActor) = compilerQueue.dequeue()
      try {
        selectCompiler(req) match {
          case Some(compilerInfo) =>
            // lastActivity BURADA da tazeleniyor. Eskiden tazelenmiyordu:
            // gönderim updateCompilerState'i atlayıp doğrudan copy(state = ...)
            // yaptığı için Compiling'e geçen derleyicinin lastActivity'si bir
            // önceki Ready anında kalıyordu. /durum'un ilk koşusunda görüldü:
            // derleyici Compiling'e yeni girmişken lastActivitySeconds=18.
            // Önemi teşhisin ötesinde: "ne zamandır Compiling" ölçüsü
            // takılma gözcüsünün (koco-deploy#17, 2. madde) dayanacağı sayı.
            //
            // Derleyici SEÇİMİNİ değiştirmiyor: selectCompiler yalnız Ready
            // olanları lastActivity'ye göre sıralıyor, Ready'deki değeri ise
            // her zaman yanıt/CompilerReady dalındaki updateCompilerState
            // yazıyor. Buradaki yazı yalnız Compiling süresince görünür.
            compilers.update(compilerInfo.id, compilerInfo.copy(state = CompilerState.Compiling, lastActivity = now))
            compilationPending += compilerInfo.id -> sourceActor
            // add default libs
            log.debug(s"Sending compiler request to ${compilerInfo.id}")
            compilerInfo.compilerService ! req.updated(src => src + defaultLibs(compilerInfo.scalaVersion))
            // process next in queue
            processQueue()
          // DİKKAT -- Left'in içindeki metin KULLANICININ EKRANINA çıkıyor.
          // Zincir: Left(...) -> WebService.CacheError -> HTTP 400 gövdesi ->
          // istemci (kojojs-editor CompilerHandler) 400'de responseText'i
          // olduğu gibi çıktı paneline basıyor. Bu yüzden Türkçe ve çocuğa
          // anlaşılır olmalı. log.error İNGİLİZCE kalıyor: o işletmeciye
          // bakıyor, aranabilir olması ve yukarı akışla eşleşmesi önemli.
          case None if compilers.isEmpty =>
            // hiç derleyici kaydolmamış -- sunucu yeni başlamış olabilir
            log.error("No compiler instance currently registered")
            sourceActor ! Left("Sunucu yeni başlıyor, derleyici henüz hazır değil. Birazdan yine deneyin.")
          case None =>
            // derleyici(ler) var ama hiçbiri Ready değil: hepsi derleme yapıyor
            log.error("No suitable compiler available")
            sourceActor ! Left("Sunucu şu anda çok yoğun. Biraz sonra yine deneyin.")
        }
      } catch {
        case e: Throwable =>
          log.error(e, s"Compilation failed")
          sourceActor ! Left(e.getMessage)
      }
    }
  }

  def receive = {
    case RegisterCompiler(id, compilerService, scalaVersion) =>
      compilers += id -> CompilerInfo(id,
                                      compilerService,
                                      scalaVersion,
                                      CompilerState.Initializing,
                                      now,
                                      "unknown",
                                      Set.empty,
                                      now)
      purgeRetiring()
      if (retiring.nonEmpty) {
        val (old, _) = retiring.minBy(_._2)
        retiring -= old
        log.info(s"Registered compiler $id for Scala $scalaVersion, replacing retired compiler $old")
      } else {
        log.debug(s"Registered compiler $id for Scala $scalaVersion")
      }
      // send current libraries
      compilerService ! UpdateLibraries(currentLibs.getOrElse(scalaVersion, Nil))
      context.watch(compilerService)

    case UnregisterCompiler(id) =>
      compilers.get(id).foreach(info => context.unwatch(info.compilerService))
      compilers -= id

    case Terminated(compilerService) =>
      // check if it still exist in the map
      compilers.find(_._2.compilerService == compilerService) match {
        case Some((id, info)) =>
          compilers -= id
        case _ =>
      }

    case CompilerPing(id) =>
      compilerSeen(id)

    case UpdateState(id, newState) =>
      updateCompilerState(id, newState)

    case req: CompilerRequest =>
      // add to the queue
      compilerQueue.enqueue((req, sender()))
      processQueue()

    case CancelCompilation(id) =>
      compilerQueue = compilerQueue.filterNot(_._1.id == id)

    case (id: String, CompilerReady) =>
      log.info(s"Compiler $id is now ready")
      updateCompilerState(id, CompilerState.Ready)
      processQueue()

    case (id: String, response: CompilerResponse) =>
      log.debug(s"Received compiler response from $id")
      updateCompilerState(id, CompilerState.Ready)
      compilationPending.get(id) match {
        case Some(actor) =>
          compilationPending -= id
          actor ! Right(response)
        case None =>
          log.error(s"No compilation pending for compiler $id")
      }
      // Yenilenme (koco-deploy#17, 0. madde): N cevaptan sonra taze JVM.
      compilers.get(id).foreach { info =>
        val served = info.served + 1
        if (recycling && served >= recycleAfter && canRecycle(info)) {
          log.info(s"Compiler $id served $served requests, retiring it for a fresh JVM")
          retire(info)
        } else {
          compilers.update(id, info.copy(served = served))
        }
      }
      processQueue()

    case DropCompiler(compilerService) =>
      context.stop(compilerService)

    case RefreshLibraries =>
      try {
        log.debug("Refreshing libraries")
        val newLibs = loadLibraries(Config.extLibs, Config.defaultLibs)
        // are there any changes?
        if (newLibs.nonEmpty && newLibs.toSet != currentLibs.toSet) {
          currentLibs = newLibs
          // inform all connected compilers
          compilers.values.foreach { comp =>
            comp.compilerService ! UpdateLibraries(currentLibs(comp.scalaVersion))
          }
          // refresh again
          context.system.scheduler.scheduleOnce(Config.refreshLibraries, context.self, RefreshLibraries)
        } else if (newLibs.isEmpty) {
          // try again soon
          context.system.scheduler.scheduleOnce(5.seconds, context.self, RefreshLibraries)
        }
      } catch {
        case e: Throwable =>
          log.error(s"Error while refreshing libraries", e)
      }

    case CheckCompilers =>
      purgeRetiring()
      val stallTimeout = Config.compilerHealth.stallTimeout.toMillis
      // toList: döngü içinde retire() haritadan siliyor
      compilers.values.toList.foreach { compiler =>
        val id = compiler.id
        if (stallTimeout > 0 && compiler.state == CompilerState.Compiling && now - compiler.lastActivity > stallTimeout) {
          // Takılma gözcüsü (koco-deploy#17, 1. madde). Ping gözcüsü (aşağıda)
          // bu hâli göremiyor: derleme bir Future'da takılı kalırken aktör
          // ping atmayı sürdürüyor. Ölçü lastActivity, yani Compiling'e
          // girildiği an (processQueue).
          log.error(
            s"Compiler $id stuck compiling for ${(now - compiler.lastActivity) / 1000} seconds, retiring it" +
              (if (recycling) " (Retire)" else " (disconnect)"))
          // İstemcinin ask'i (WebService, 30 sn) büyük olasılıkla çoktan
          // zaman aşımına uğradı; cevap o zaman ölü mektuba düşer, zararsız.
          compilationPending
            .remove(id)
            .foreach(_ ! Left("Derleme çok uzun sürdü, derleyici yeniden başlatılıyor. Biraz sonra yine deneyin."))
          retire(compiler)
        } else if (now - compiler.lastSeen > 120 * 1000) {
          log.error(s"Compiler service $id not seen in ${(now - compiler.lastSeen) / 1000} seconds, terminating compiler")
          context.stop(compiler.compilerService)
        }
      }

    case GetStatus =>
      sender() ! RouterStatus(
        registered = compilers.size,
        ready = compilers.values.count(_.state == CompilerState.Ready),
        compiling = compilers.values.count(_.state == CompilerState.Compiling),
        initializing = compilers.values.count(_.state == CompilerState.Initializing),
        queued = compilerQueue.size,
        pending = compilationPending.size,
        restarting = { purgeRetiring(); retiring.size },
        compilers = compilers.values.toSeq.sortBy(_.id).map { c =>
          // lastActivity yalnız durum değişiminde yazılıyor (ping ona
          // DOKUNMUYOR), yani "ne zamandır bu durumda" ölçüsü budur.
          // lastSeen ise ping'le tazeleniyor: "süreç yaşıyor mu" sorusuna o
          // bakıyor. İkisinin yan yana görünmesi asıl mesele: arızanın
          // profili olan "yaşıyor, ping atıyor, ama Compiling'de takılı"
          // hâli ancak ikisi birlikte okununca görülüyor -- yüksek
          // lastActivitySeconds + düşük lastSeenSeconds + state=Compiling.
          CompilerStatus(c.id,
                         c.scalaVersion,
                         c.state.toString,
                         (now - c.lastActivity) / 1000,
                         (now - c.lastSeen) / 1000,
                         c.served)
        }
      )

    case other =>
      // .take(150): kardeş aktörün normu (CompilerService.scala:68). Bu satır
      // bu değişiklikle ilk kez konteyner günlüğüne de düşüyor, o yüzden
      // sınırsız bırakmak artık ucuz değil. Kaynak taşıyan mesaj buraya
      // DÜŞEMEZ (`case req: CompilerRequest` tipe göre yakalıyor), yani
      // sızıntı değil; hijyen ve tutarlılık.
      log.error(s"Received unknown message ${other.toString.take(150)}")
  }
}

object CompilerManager {
  def props = Props(new CompilerManager)

  // Retire'dan sonra derleyicinin kendi çıkması için tanınan süre.
  val retireGrace = 10.seconds

  case class CompilerInfo(id: String,
                          compilerService: ActorRef,
                          scalaVersion: String,
                          state: CompilerState,
                          lastActivity: Long,
                          lastClient: String,
                          lastLibs: Set[ExtLib],
                          lastSeen: Long,
                          served: Int = 0)

}
