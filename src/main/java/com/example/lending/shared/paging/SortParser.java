package com.example.lending.shared.paging;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.example.lending.shared.error.BusinessException;

/**
 * 要求の {@code sort=name,desc} を {@link SortOrder} にする。
 * キーは enum の名前を camelCase にした値（{@code ASSET_CODE} → {@code assetCode}）だけ、向きは asc・desc だけを受け付け、
 * それ以外は 400（VALIDATION_FAILED・errors の parameter は sort）にする。
 */
public final class SortParser {

	static final String PARAMETER = "sort";

	private SortParser() {
	}

	public static <K extends Enum<K>> SortOrder<K> parse(String value, Class<K> keyType, SortOrder<K> defaultOrder) {
		if (value == null || value.isBlank()) {
			return defaultOrder;
		}
		String[] parts = value.split(",", -1);
		if (parts.length > 2) {
			throw invalidKey(keyType);
		}
		K key = Arrays.stream(keyType.getEnumConstants())
			.filter(candidate -> apiName(candidate).equals(parts[0]))
			.findFirst()
			.orElseThrow(() -> invalidKey(keyType));
		if (parts.length == 1 || parts[1].equals("asc")) {
			return SortOrder.asc(key);
		}
		if (parts[1].equals("desc")) {
			return SortOrder.desc(key);
		}
		throw BusinessException.invalidParameter(PARAMETER, "並び替えの向きは asc か desc で指定してください");
	}

	/** API で使う名前（{@code CREATED_AT} → {@code createdAt}）。 */
	public static String apiName(Enum<?> key) {
		String[] words = key.name().toLowerCase(Locale.ROOT).split("_");
		StringBuilder name = new StringBuilder(words[0]);
		for (int i = 1; i < words.length; i++) {
			name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
		}
		return name.toString();
	}

	private static BusinessException invalidKey(Class<? extends Enum<?>> keyType) {
		String allowed = Arrays.stream(keyType.getEnumConstants())
			.map(SortParser::apiName)
			.collect(Collectors.joining(", "));
		return BusinessException.invalidParameter(PARAMETER,
				"並び替えのキーは次のどれかを「キー」か「キー,asc」「キー,desc」の形で指定してください: " + allowed);
	}

}
