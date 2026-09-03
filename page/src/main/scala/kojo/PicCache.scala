package kojo

object PicCache {
  var hits = 0
  var misses = 0
  val seen = new java.util.concurrent.ConcurrentHashMap[Picture, Int]()
  def clear(): Unit = {
    seen.clear()
    hits = 0
    misses = 0
  }
  def freshPic(pic: Picture): Picture = {
    if (seen.containsKey(pic)) {
      val ret = pic.copy
      seen.put(ret, 0)
      hits += 1
      ret
    }
    else {
      seen.put(pic, 0)
      misses += 1
      pic
    }
  }
  // 2.13: Seq artık immutable.Seq; mutable çağıranlar (ArrayBuffer vb.) da
  // gelebilsin diye parametre collection.Seq, dönüş yine immutable.
  def freshPics(ps: collection.Seq[Picture]): Seq[Picture] = {
    ps.iterator.map(freshPic).toSeq
  }
}
