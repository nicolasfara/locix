/*
 * Focused excerpt from Scalagna example/src/main/scala-2.11/example/GraffitiWebSockets.scala
 * The original page also depends on the shared ColorPicker.scala helper.
 */

package example

import scalamt._
import scalamt.macros._
import scalamt.services.MTService
import scalatags.Text.all._
import scalatags.Text.tags2

object GraffitiWebSockets extends MTPage {
  type Draw = ((Int, Int, Int), Double, (Double, Double), (Double, Double))
  case class Add(value: Draw)

  override def execute(): List[MTService] = {
    val width: Int = 700
    val height: Int = 300
    var sliderSize: Int = 25
    val canvasID: String = "canvas"

    @server @inject var drawEvents: List[Draw] = List.empty

    @server val receiveMessage: Add => Unit = { add =>
      drawEvents.value = add.value :: drawEvents.value
    }

    val wsMessage = webSocketService[Add](receiveMessage)

    @client val _canvas: (org.scalajs.dom.html.Canvas,
                          org.scalajs.dom.CanvasRenderingContext2D) = {
      import org.scalajs.dom._
      val canvas = document.getElementById(canvasID).asInstanceOf[html.Canvas]
      val ctx = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
      (canvas, ctx)
    }

    @client @fragment val changeSliderFragment = {
      import org.scalajs.dom._

      (e: Element, _: Event) =>
        sliderSize = Integer.parseInt(e.asInstanceOf[html.Input].value)
    }

    @client def draw(ctx: org.scalajs.dom.CanvasRenderingContext2D)(
        color: (Int, Int, Int),
        size: Double,
        p1: (Double, Double),
        p2: (Double, Double)): Unit = {
      ctx.strokeStyle = s"rgb(${color._1}, ${color._2}, ${color._3})"
      ctx.lineWidth = size
      ctx.beginPath()
      ctx.moveTo(p1._1, p1._2)
      ctx.lineTo(p2._1, p2._2)
      ctx.stroke()
    }

    @client val init_client = {
      import org.scalajs.dom._

      ColorPicker.load()

      val (canvas, ctx): (html.Canvas, CanvasRenderingContext2D) = _canvas.value
      ctx.lineCap = "round"

      var x: Double = 0
      var y: Double = 0

      drawEvents.inject.foreach((m: Draw) => draw(ctx)(m._1, m._2, m._3, m._4))

      def set_coord(ev: MouseEvent): Unit = {
        val bcr = canvas.getBoundingClientRect()
        x = ev.clientX - bcr.left
        y = ev.clientY - bcr.top
      }

      def compute_line(ev: MouseEvent): Draw = {
        val oldx = x
        val oldy = y
        set_coord(ev)
        (ColorPicker.selectedColor.value, sliderSize, (oldx, oldy), (x, y))
      }

      val line = { ev: MouseEvent =>
        val r = compute_line(ev)
        draw(ctx)(r._1, r._2, r._3, r._4)
        wsMessage.send(Add(r))
      }

      var down = false

      canvas.onmouseup = (e: MouseEvent) => down = false
      canvas.onmousemove = (e: MouseEvent) => if (down) line(e)
      canvas.onmousedown = (e: MouseEvent) => {
        set_coord(e)
        down = true
      }

      wsMessage.onmessage { add =>
        val d = add.value
        draw(ctx)(d._1, d._2, d._3, d._4)
      }
    }

    @server def canvas_elt: Frag =
      canvas(id := canvasID,
             style := "border: solid 1px #aaaaaa",
             widthA := width,
             heightA := height)

    @server def bodyMain: Seq[Frag] = Seq(
      div(
        `class` := "container",
        div(`class` := "row", div(`class` := "col", h1("Graffiti"))),
        div(`class` := "row",
            div(`class` := "col", canvas_elt.value),
            div(`class` := "col",
                div(`class` := "row", ColorPicker.make.value))),
        div(
          `class` := "row",
          div(`class` := "col",
              input(`type` := "range",
                    min := "1",
                    max := "80",
                    value := s"$sliderSize",
                    style := s"width: ${width}px",
                    onchange := changeSliderFragment))
        )
      )
    )

    @server def headMain: Seq[Frag] = Seq(
      link(rel := "stylesheet",
           href := s"/${MTServerSettings.mtResourceDir}/bootstrap.min.css"),
      link(
        rel := "stylesheet",
        href := s"/${MTServerSettings.mtResourceDir}/bootstrap-grid.min.css"),
      tags2.title("Graffiti")
    )

    List(htmlService("/graff/ws", bodyMain, headMain), wsMessage)
  }
}
