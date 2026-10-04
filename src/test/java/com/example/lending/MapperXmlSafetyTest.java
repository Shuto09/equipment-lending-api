package com.example.lending;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文書と実装のずれを機械で止める検査（docs/テスト計画.md §3）。
 * <ul>
 * <li>マッパー XML に文字列の埋め込み（ドル記号＋波括弧）が無い＝SQL 注入の経路を作らない。</li>
 * <li>マッパー XML に DB の時刻関数が無い＝時刻はアプリの Clock から渡す。</li>
 * <li>SQL を MyBatis の注釈で書いていない（注釈側なら埋め込みも書けてしまう）。</li>
 * </ul>
 */
class MapperXmlSafetyTest {

	private static final Path MAPPER_DIR = Path.of("src", "main", "resources", "mapper");

	private static final Path JAVA_DIR = Path.of("src", "main", "java");

	private static final Pattern STRING_SUBSTITUTION = Pattern.compile("\\$\\{[^}]*}?");

	private static final Pattern BIND_VARIABLE = Pattern.compile("#\\{[^}]*}");

	private static final Pattern TIME_FUNCTION = Pattern.compile("(?i)\\b(?:now\\s*\\(|current_date\\b|current_time\\b"
			+ "|current_timestamp\\b|localtime\\b|localtimestamp\\b|clock_timestamp\\s*\\(|statement_timestamp\\s*\\("
			+ "|transaction_timestamp\\s*\\(|timeofday\\s*\\()");

	private static final Pattern SQL_ANNOTATION = Pattern
		.compile("@(?:org\\.apache\\.ibatis\\.annotations\\.)?(?:Select|Insert|Update|Delete)(?:Provider)?\\s*\\("
				+ "|import\\s+org\\.apache\\.ibatis\\.annotations\\.(?:\\*|(?:Select|Insert|Update|Delete)(?:Provider)?\\s*;)");

	static List<String> findStringSubstitutions(String text) {
		return findAll(STRING_SUBSTITUTION, text);
	}

	/** バインド変数（#{now} など）の中は値の名前なので、取り除いてから探す。 */
	static List<String> findTimeFunctions(String text) {
		return findAll(TIME_FUNCTION, BIND_VARIABLE.matcher(text).replaceAll(""));
	}

	static List<String> findSqlAnnotations(String text) {
		return findAll(SQL_ANNOTATION, text);
	}

	@Test
	@DisplayName("マッパー XML に文字列の埋め込みが 1 つも無い")
	void mapperXml_hasNoStringSubstitution() {
		mapperXmlFiles().forEach(file -> assertThat(findStringSubstitutions(read(file))).as(file.toString()).isEmpty());
	}

	@Test
	@DisplayName("マッパー XML に DB の時刻関数が 1 つも無い")
	void mapperXml_hasNoTimeFunction() {
		mapperXmlFiles().forEach(file -> assertThat(findTimeFunctions(read(file))).as(file.toString()).isEmpty());
	}

	@Test
	@DisplayName("src/main/java に MyBatis の SQL 注釈が 1 つも無い")
	void javaSources_haveNoSqlAnnotation() {
		List<Path> files = filesUnder(JAVA_DIR, ".java");
		assertThat(files).isNotEmpty();
		files.forEach(file -> assertThat(findSqlAnnotations(read(file))).as(file.toString()).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "ORDER BY ${column}", "WHERE id = ${x}", "${" })
	@DisplayName("検査ロジック: 文字列の埋め込みを見つける")
	void detects_stringSubstitution(String text) {
		assertThat(findStringSubstitutions(text)).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "SET lent_at = NOW()", "now ()", "localtimestamp", "WHERE due_date < CURRENT_DATE",
			"current_timestamp", "LOCALTIME", "clock_timestamp()", "statement_timestamp()", "transaction_timestamp()",
			"timeofday()", "current_time" })
	@DisplayName("検査ロジック: 時刻関数を見つける")
	void detects_timeFunction(String text) {
		assertThat(findTimeFunctions(text)).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "@Select(\"SELECT 1\")", "@SelectProvider(type = X.class)", "@Update (\"x\")",
			"@org.apache.ibatis.annotations.Insert(\"x\")", "import org.apache.ibatis.annotations.Delete;",
			"import org.apache.ibatis.annotations.*;" })
	@DisplayName("検査ロジック: SQL の注釈を見つける")
	void detects_sqlAnnotation(String text) {
		assertThat(findSqlAnnotations(text)).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "WHERE due_date < #{today}", "SET returned_at = #{now}", "#{currentDate}",
			"#{localtime}", "l.due_date, l.lent_at", "nowhere", "@Mapper", "import org.apache.ibatis.annotations.Param;" })
	@DisplayName("検査ロジック: バインド変数や無関係な語は見つけない")
	void ignores_bindVariablesAndOtherWords(String text) {
		assertThat(findStringSubstitutions(text)).isEmpty();
		assertThat(findTimeFunctions(text)).isEmpty();
		assertThat(findSqlAnnotations(text)).isEmpty();
	}

	private static List<Path> mapperXmlFiles() {
		List<Path> files = filesUnder(MAPPER_DIR, ".xml");
		assertThat(files).as("マッパー XML が見つからない（検査が空振りしていないか）").isNotEmpty();
		return files;
	}

	private static List<Path> filesUnder(Path dir, String suffix) {
		try (Stream<Path> paths = Files.walk(dir)) {
			return paths.filter(path -> path.toString().endsWith(suffix)).toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static List<String> findAll(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		return matcher.results().map(result -> result.group()).toList();
	}

}
