//> using scala "3.7.3"

import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.sys.process.*

final case class Mapping(
    source: String,
    target: String,
    sourceLines: Option[String] = None,
    targetLines: Option[String] = None,
)
final case class Result(mapping: Mapping, sourceLoc: Either[String, Int], targetLoc: Either[String, Int])
final case class LineSpan(start: Int, end: Int)

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
    Result(
      m,
      countWithOptionalLines(repoRoot.resolve(m.source), m.sourceLines),
      countWithOptionalLines(repoRoot.resolve(m.target), m.targetLines),
    )
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
  val srcItemPattern = """^\s*-\s*source:\s*(.+)\s*$""".r
  val keyPattern = """^\s*([a-zA-Z_][a-zA-Z0-9_-]*)\s*:\s*(.+)\s*$""".r

  val buffer = ListBuffer.empty[Mapping]
  var source: Option[String] = None
  var target: Option[String] = None
  var sourceLines: Option[String] = None
  var targetLines: Option[String] = None

  def flushCurrent(): Unit = {
    for
      src <- source
      tgt <- target
    do buffer += Mapping(src, tgt, sourceLines, targetLines)

    source = None
    target = None
    sourceLines = None
    targetLines = None
  }

  for (line <- Files.readAllLines(path).asScala) {
    line match
      case srcItemPattern(value) =>
        flushCurrent()
        source = cleanMappingValue(value)
      case keyPattern(key, value) if source.nonEmpty =>
        key match
          case "source"       => source = cleanMappingValue(value)
          case "target"       => target = cleanMappingValue(value)
          case "source_lines" => sourceLines = cleanMappingValue(value)
          case "target_lines" => targetLines = cleanMappingValue(value)
          case _              =>
      case _ =>
  }

  flushCurrent()
  buffer.filterNot(_.source.contains("???")).toList
}

def cleanMappingValue(value: String): Option[String] = {
  val trimmed = value.trim
  if (trimmed.isEmpty) None
  else {
    val unquoted =
      if (trimmed.length >= 2 && ((trimmed.head == '"' && trimmed.last == '"') || (trimmed.head == '\'' && trimmed.last == '\'')))
        trimmed.substring(1, trimmed.length - 1)
      else trimmed
    Some(unquoted.trim).filter(_.nonEmpty)
  }
}

def countWithOptionalLines(path: Path, lineSpec: Option[String]): Either[String, Int] =
  lineSpec match
    case Some(spec) => clocCountForLineSpec(path, spec)
    case None       => clocCount(path)

def clocCountForLineSpec(path: Path, lineSpec: String): Either[String, Int] = {
  if (!Files.exists(path)) Left("missing")
  else if (!Files.isRegularFile(path)) Left("line range requires regular file")
  else {
    parseLineSpec(lineSpec).flatMap { spans =>
      val lines = Files.readAllLines(path).asScala.toIndexedSeq
      val maxLine = lines.length

      spans.find(_.end > maxLine) match
        case Some(_) => Left(s"line range out of bounds (max line $maxLine)")
        case None =>
          val selected = spans.flatMap(span => lines.slice(span.start - 1, span.end))
          val fileName = Option(path.getFileName).map(_.toString).getOrElse("slice.txt")
          val tempFile = Files.createTempFile("sloc-lines-", s"-$fileName")
          try {
            Files.write(tempFile, selected.asJava)
            clocCount(tempFile)
          } catch {
            case e: Throwable => Left(s"line-range count error: ${e.getMessage}")
          } finally Files.deleteIfExists(tempFile)
    }
  }
}

def parseLineSpec(spec: String): Either[String, List[LineSpan]] = {
  val tokens = spec.split(',').iterator.map(_.trim).filter(_.nonEmpty).toList
  if (tokens.isEmpty) Left("invalid line range syntax: empty")
  else {
    val parsed = tokens.map(parseLineToken)
    parsed.collectFirst { case Left(err) => err } match
      case Some(err) => Left(err)
      case None      => Right(parsed.collect { case Right(span) => span })
  }
}

def parseLineToken(token: String): Either[String, LineSpan] = {
  val singleLinePattern = raw"(\d+)".r
  val rangePattern = raw"(\d+)-(\d+)".r

  token match
    case singleLinePattern(n) =>
      toPositiveLineNumber(n).map(line => LineSpan(line, line))
    case rangePattern(start, end) =>
      for
        startLine <- toPositiveLineNumber(start)
        endLine <- toPositiveLineNumber(end)
        span <-
          if (startLine <= endLine) Right(LineSpan(startLine, endLine))
          else Left(s"invalid line range '$token': start > end")
      yield span
    case _ => Left(s"invalid line range syntax: '$token'")
}

def toPositiveLineNumber(value: String): Either[String, Int] =
  value.toIntOption match
    case Some(line) if line > 0 => Right(line)
    case _                      => Left(s"invalid line range syntax: '$value'")

def clocCount(path: Path): Either[String, Int] = {
  if (!Files.exists(path)) Left("missing")
  else {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val exit = Process(Seq("cloc", "--csv", "--quiet", path.toString))
      .!(ProcessLogger(line => stdout.append(line).append('\n'), err => stderr.append(err).append('\n')))

    val hasErrorInOutput = (stdout.toString + "\n" + stderr.toString).toLowerCase.contains("error")
    if (exit != 0 || hasErrorInOutput) countFileLines(path)
    else {
      val lines = stdout.toString.linesIterator.filterNot(_.trim.isEmpty).toList
      val dataLineOpt = lines.reverse.find(line => !line.startsWith("files"))
      dataLineOpt
        .flatMap(line => line.split(',').map(_.trim).lastOption.flatMap(_.toIntOption))
        .toRight("unexpected cloc output")
        .orElse(countFileLines(path))
    }
  }
}

def countFileLines(path: Path): Either[String, Int] = {
  def countLinesInFile(file: Path): Int = {
    val reader = Files.newBufferedReader(file)
    try Iterator.continually(reader.readLine()).takeWhile(_ != null).size
    finally reader.close()
  }

  try {
    if (Files.isRegularFile(path)) Right(countLinesInFile(path))
    else if (Files.isDirectory(path)) {
      val stream = Files.walk(path)
      try {
        val total = stream.iterator().asScala
          .filter(Files.isRegularFile(_))
          .map(countLinesInFile)
          .sum
        Right(total)
      } finally stream.close()
    } else Left("not a regular file or directory")
  } catch {
    case e: Throwable => Left(s"line-count error: ${e.getMessage}")
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
