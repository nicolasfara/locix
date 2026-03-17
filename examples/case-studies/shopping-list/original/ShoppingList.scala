/*
 * Focused excerpt from Scalagna example/src/main/scala-2.11/example/ShoppingList.scala
 */

package example

import scalamt._
import scalamt.macros._
import scalamt.services.MTService
import scalatags.Text.all.{div, _}

/**
  * Created by Michael on 10/11/2016.
  * Shopping list application using RPC
  */
object ShoppingList extends MTPage {
  override def execute(): List[MTService] = {

    @server
    def getAllUsers: List[String] = List("Mom", "Dad", "John")

    @server
    val counterStart = 0

    @server @inject
    var counter: Int = counterStart.value

    @server
    val _ = {
      counter.value = 10
    }

    type ListItem = (String, String)

    @server @inject
    var shoppingList: List[ListItem] = List.empty

    @client
    val initClient: Unit = {
      import org.scalajs.dom.{html, document}
      import scalatags.JsDom.all._

      val list = document.getElementById("shoppinglist").asInstanceOf[html.UList]

      shoppingList.inject.foreach((item: ListItem) => list.appendChild(li(s"${item._2} (${item._1})").render))
    }

    @server
    def increaseCounter(e: Int): String = {
      counter.value = counter.value + e
      s"Counter increased, current value: ${counter.value}"
    }

    @server
    val addToShoppingList = (e: ListItem) => {
      shoppingList.value = e :: shoppingList.value
      s"New item $e added to the shoppinglist, new value: $shoppingList"
    }

    @server
    val clearShoppingList: (Unit => String) = _ => {
      shoppingList.value = List.empty
      "List cleared"
    }

    val rpcIncrease = rpcService[Int, String]("/increase", increaseCounter _)
    val rpcShoppingList = rpcService[ListItem, String]("/shoppinglist", addToShoppingList)
    val rpcClearShoppingList = rpcService[Unit, String]("/clearshoppinglist", clearShoppingList)

    @client
    @fragment
    val onclickIncrease =
      (_: org.scalajs.dom.Element, _: org.scalajs.dom.Event) => rpcIncrease.call(1)

    @client
    @fragment
    val onclickClear =
      (_: org.scalajs.dom.Element, _: org.scalajs.dom.Event) => rpcClearShoppingList.call(())

    @client
    @fragment
    val onclickAdd = {
      import org.scalajs.dom.{html, document, Element, Event}
      import scalatags.JsDom.all._

      val input = document.getElementById("input").asInstanceOf[html.Input]
      val list = document.getElementById("shoppinglist").asInstanceOf[html.UList]
      val nameselector = document.getElementById("name").asInstanceOf[html.Select]

      (_: Element, _: Event) => {
        val name = nameselector.value
        val item = input.value
        if (!item.isEmpty) {
          list.appendChild(li(s"$item ($name)").render)
          rpcShoppingList.call((name, item), (_: String) => input.value = "")
        }
      }
    }

    @server
    val _head: Seq[Frag] = Seq(
      link(rel := "stylesheet",
        href := s"/${MTServerSettings.mtResourceDir}/bootstrap.min.css"
      )
    )

    @server
    def _body: Seq[Frag] = Seq(
      div(
        marginLeft := "10%",
        marginRight := "10%",
        backgroundColor := "lightgray",
        div(
          padding := "25 25 25 25",
          minHeight := "100%",

          div(
            textAlign := "center",
            h1(
              s"Shopping list application - counter: $counter ",
              button("+", `class` := "btn btn-lg", onclick := onclickIncrease)
            )
          ),
          div(
            textAlign := "center",
            "Name: ",
            select(id := "name", getAllUsers.value.map(option(_))),
            " Item: ",
            input(id := "input"), " ",
            button("Add", `class` := "btn btn-sm", onclick := onclickAdd),
            button("Clear", `class` := "btn btn-sm", onclick := onclickClear)
          ),
          div(
            "ShoppingList: ",
            ul(id := "shoppinglist")
          )
        )
      )
    )

    List(
      htmlService("/shopping", _body, _head),
      rpcClearShoppingList,
      rpcIncrease,
      rpcShoppingList
    )
  }
}
