import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

def app() = div(
  cls := "container mx-auto text-slate-50",
  h1("Hello Vite!")
)

@main def main(): Unit =
  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    app()
  )
