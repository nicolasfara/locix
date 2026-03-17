package io.github.locix

object GraffitiExampleSupport:
  final case class Color(red: Int, green: Int, blue: Int)
  final case class Point(x: Double, y: Double)
  final case class Draw(color: Color, size: Double, from: Point, to: Point)

  sealed trait RpcAction
  final case class Add(draw: Draw) extends RpcAction
  case object ClearCanvas extends RpcAction

  val clientIds: List[String] = List("alice", "bob", "carol")

  val rpcStartupDelayMs: Map[String, Long] = Map(
    "alice" -> 500L,
    "bob" -> 950L,
    "carol" -> 1400L,
  )

  val websocketStartupDelayMs: Map[String, Long] = Map(
    "alice" -> 500L,
    "bob" -> 900L,
    "carol" -> 1300L,
  )

  val betweenActionsDelayMs: Long = 140L

  val rpcScripts: Map[String, List[RpcAction]] = Map(
    "alice" -> List(
      Add(Draw(Color(255, 64, 64), 12.0, Point(15.0, 30.0), Point(95.0, 45.0))),
      Add(Draw(Color(255, 64, 64), 12.0, Point(95.0, 45.0), Point(140.0, 95.0))),
    ),
    "bob" -> List(
      Add(Draw(Color(64, 128, 255), 18.0, Point(200.0, 35.0), Point(260.0, 85.0))),
      ClearCanvas,
      Add(Draw(Color(64, 128, 255), 14.0, Point(175.0, 110.0), Point(255.0, 155.0))),
    ),
    "carol" -> List(
      Add(Draw(Color(32, 180, 96), 10.0, Point(40.0, 150.0), Point(120.0, 160.0))),
    ),
  )

  val websocketScripts: Map[String, List[Draw]] = Map(
    "alice" -> List(
      Draw(Color(255, 96, 0), 16.0, Point(25.0, 20.0), Point(90.0, 70.0)),
      Draw(Color(255, 96, 0), 16.0, Point(90.0, 70.0), Point(150.0, 55.0)),
    ),
    "bob" -> List(
      Draw(Color(0, 140, 255), 20.0, Point(180.0, 45.0), Point(250.0, 80.0)),
      Draw(Color(0, 140, 255), 20.0, Point(250.0, 80.0), Point(295.0, 135.0)),
    ),
    "carol" -> List(
      Draw(Color(120, 32, 200), 9.0, Point(60.0, 120.0), Point(145.0, 180.0)),
      Draw(Color(120, 32, 200), 9.0, Point(145.0, 180.0), Point(210.0, 165.0)),
    ),
  )

  def renderColor(color: Color): String =
    s"rgb(${color.red},${color.green},${color.blue})"

  def renderPoint(point: Point): String =
    f"(${point.x}%.1f, ${point.y}%.1f)"

  def renderDraw(draw: Draw): String =
    s"${renderColor(draw.color)} size=${draw.size} ${renderPoint(draw.from)} -> ${renderPoint(draw.to)}"

  def renderHistory(history: List[Draw]): String =
    if history.isEmpty then "<empty>"
    else history.zipWithIndex.map((draw, index) => s"#${index + 1} ${renderDraw(draw)}").mkString(" | ")
end GraffitiExampleSupport
