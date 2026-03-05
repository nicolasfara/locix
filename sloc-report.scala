//> using scala "3.7.3"

import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.sys.process.*

final case class Mapping(source: String, target: String)
final case class Result(mapping: Mapping, sourceLoc: Either[String, Int], targetLoc: Either[String, Int])

@main def run(outputArg: String*): Unit = {
  val repoRoot = Paths.get("")
  val mappingPath = repoRoot.resolve("examples-mapping.yml")

  requireCloc()

  val mappings = readMappings(mappingPath)
  if (mappings.isEmpty) {
    System.err.println("No mappings found in examples-mapping.yml")
    sys.exit(1)
  }

  val results = mappings.map { m =>
    Result(m, clocCount(repoRoot.resolve(m.source)), clocCount(repoRoot.resolve(m.target)))
  }

  val report = formatTable(results)
  println(report)

  val outputPath = outputArg.headOption.map(Paths.get(_)).getOrElse(repoRoot.resolve("sloc-report.txt"))
  Option(outputPath.getParent).foreach { parent => if (!Files.exists(parent)) Files.createDirectories(parent) }
  Files.writeString(outputPath, report)
  println(s"\nSaved report to ${outputPath.toAbsolutePath.normalize}")
}

def requireCloc(): Unit = {
  val exit = try Process(Seq("cloc", "--version")).!(ProcessLogger(_ => ())) catch case _: Throwable => -1
  if (exit != 0) {
    System.err.println("cloc is required but not found. Install cloc and retry.")
    sys.exit(1)
  }
}

def readMappings(path: Path): List[Mapping] = {
  val srcPattern = """^\s*-\s*source:\s*(.+)\s*$""".r
  val tgtPattern = """^\s*target:\s*(.+)\s*$""".r

  val buffer = ListBuffer.empty[Mapping]
  var pendingSource: Option[String] = None

  for (line <- Files.readAllLines(path).asScala) {
    line match
      case srcPattern(value) => pendingSource = Some(value.trim)
      case tgtPattern(value) =>
        pendingSource.foreach(src => buffer += Mapping(src, value.trim))
        pendingSource = None
      case _ =>
  }

  buffer.filterNot(_.source.contains("???")).toList
}

def clocCount(path: Path): Either[String, Int] = {
  if (!Files.exists(path)) Left("missing")
  else {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val exit = Process(Seq("cloc", "--csv", "--quiet", path.toString))
      .!(ProcessLogger(line => stdout.append(line).append('\n'), err => stderr.append(err).append('\n')))

    if (exit != 0) {
      val msg = (stderr.toString + stdout.toString).trim
      Left(if (msg.nonEmpty) s"cloc error: $msg" else "cloc error")
    } else {
      val lines = stdout.toString.linesIterator.filterNot(_.trim.isEmpty).toList
      val dataLineOpt = lines.reverse.find(line => !line.startsWith("files"))
      dataLineOpt match
        case Some(line) =>
          val parts = line.split(',').map(_.trim)
          parts.lastOption.flatMap(_.toIntOption).toRight("unexpected cloc output")
        case None => Left("unexpected cloc output")
    }
  }
}

def formatTable(results: List[Result]): String = {
  val header = Seq("Source", "Target", "Source SLOC", "Target SLOC")
  val rows = results.map { r =>
    Seq(
      r.mapping.source,
      r.mapping.target,
      r.sourceLoc.fold(identity, _.toString),
      r.targetLoc.fold(identity, _.toString)
    )
  }

  val allRows = header +: rows
  val widths = header.indices.map(i => allRows.map(_(i).length).max)

  def formatRow(row: Seq[String]) = row.zip(widths).map { case (cell, width) => cell.padTo(width, ' ') }.mkString(" | ")
  val divider = widths.map(w => "-" * w).mkString("-+-")

  (formatRow(header) + "\n" + divider + "\n" + rows.map(formatRow).mkString("\n")).stripTrailing
}
