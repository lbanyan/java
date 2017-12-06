package com.github.obase.mysql.syntax;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 针对标准SQL语法处理相关逻辑.另外拓展Mysql的双引号与反引号.
 */
public class SqlKit {

	public static final char SPACE = '\u0020';

	// 先去除`,再添加`
	public static String identifier(String val) {
		return new StringBuilder(256).append('`').append(val.replace("`", "")).append('`').toString();
	}

	// 解析sql为pstmt对象
	public static Sql parseSql(String sql) {

		List<Holder> holders = parseHolder(sql);
		Collections.reverse(holders);

		LinkedList<String> params = new LinkedList<String>();
		StringBuilder psql = new StringBuilder(sql);
		for (Holder h : holders) {
			params.addFirst(h.name);
			psql.replace(h.start, h.end, "?");
		}

		return new Sql(psql.toString(), params);
	}

	// 解析sql中的:name占位符
	public static List<Holder> parseHolder(String sql) {

		LinkedList<Holder> holders = new LinkedList<Holder>();
		int start = 0;
		int end = 0;
		int len = sql.length();
		while (end < len) {
			start = indexOf(Matcher.JavaIdentifier, sql, end, len);
			if (start == -1) {
				break;
			}
			end = indexOfNot(Matcher.JavaIdentifier, sql, start, len);
			if (end == -1) {
				end = len;
			}
			if (start > 0 && sql.charAt(start - 1) == ':') {
				holders.add(new Holder(sql.substring(start, end), start - 1, end));
			}
		}
		return holders;
	}

	// 去除SQL每行首尾的空白符
	public static String trimLine(String psql) {
		StringBuilder sb = new StringBuilder(psql.length());
		BufferedReader reader = new BufferedReader(new StringReader(psql));
		try {
			for (String line; (line = reader.readLine()) != null;) {
				line = line.trim();
				if (line.length() > 0) {
					sb.append(line).append('\n');
				}
			}
			return sb.toString();
		} catch (IOException e) {
			return psql;
		}
	}

	// 从start开始查找下一个非空白字符
	public static int indexOfNot(Matcher m, String psql, int start, int len) {
		while (start < len) {
			if (!m.match(psql.charAt(start))) {
				return start;
			}
			start++;
		}
		return -1;
	}

	// 从start开始查找下一个空白字符,中间智能跳过单引,双引,反引等特殊SQL字符
	public static int indexOf(Matcher m, String psql, int start, int len) {
		while (start < len) {
			char ch = psql.charAt(start);
			if (m.match(ch)) {
				return start;
			}
			switch (ch) {
			case '\'':
				while (++start < len) {
					ch = psql.charAt(start);
					if (ch == '\'') {
						// 前向检查,还是单引,表示该字符是转义
						int nxt = start + 1;
						if (nxt < len && psql.charAt(nxt) == '\'') {
							start++;
						} else {
							break;
						}
					} else if (ch == '\\') {
						start++;
					}
				}
				break;
			case '\"':
				while (++start < len) {
					ch = psql.charAt(start);
					if (ch == '"') {
						break;
					} else if (ch == '\\') {
						start++;
					}
				}
				break;
			case '`':
				while (++start < len) {
					ch = psql.charAt(start);
					if (ch == '`') {
						// 前向检查,还是反引,表示该字符是转义
						int nxt = start + 1;
						if (nxt < len && psql.charAt(nxt) == '`') {
							start++;
						} else {
							break;
						}
					} else if (ch == '\\') {
						start++;
					}
				}
				break;
			case '/':
				ch = psql.charAt(start + 1);
				// 从/*到*/
				if (ch == '*') {
					start = psql.indexOf("*/", start += 2); // 后移2个字符
					if (start == -1) { // 如果未找到则位移至最后
						start = len;
					}
				}
				break;
			case '#':
				// 一直到行尾
				while (++start < len) {
					ch = psql.charAt(start);
					if (ch == '\r' || ch == '\n') {
						break;
					}
				}
				break;
			case '-':
				ch = psql.charAt(start + 1);
				if (ch == '-') {
					// 一直到行尾
					while (++start < len) {
						ch = psql.charAt(start);
						if (ch == '\r' || ch == '\n') {
							break;
						}
					}
				}
				break;
			}
			start++;
		}
		return -1;
	}

	// 从start开始查找下一个空白字符,中间智能跳过单引,双引,反引等特殊SQL字符
	public static int indexOfIncludeParent(Matcher m, String psql, int start, int len) {
		int left = 0;
		while (start < len) {
			char ch = psql.charAt(start);
			if (left == 0 && m.match(ch)) {
				return start;
			}
			switch (ch) {
			case '\'':
				while (++start < len) {
					ch = psql.charAt(start);
					if (ch == '\'') {
						// 前向检查,还是单引,表示该字符是转义
						int nxt = start + 1;
						if (nxt < len && psql.charAt(nxt) == '\'') {
							start++;
						} else {
							break;
						}
					} else if (ch == '\\') {
						start++;
					}
				}
				break;
			case '\"':
				while (++start < len) {
					ch = psql.charAt(start);
					if (ch == '"') {
						break;
					} else if (ch == '\\') {
						start++;
					}
				}
				break;
			case '`':
				while (++start < len) {
					ch = psql.charAt(start);
					if (ch == '`') {
						// 前向检查,还是反引,表示该字符是转义
						int nxt = start + 1;
						if (nxt < len && psql.charAt(nxt) == '`') {
							start++;
						} else {
							break;
						}
					} else if (ch == '\\') {
						start++;
					}
				}
				break;
			case '/':
				ch = psql.charAt(start + 1);
				// 从/*到*/
				if (ch == '*') {
					start = psql.indexOf("*/", start += 2); // 后移2个字符
					if (start == -1) { // 如果未找到则位移至最后
						start = len;
					}
				}
				break;
			case '#':
				// 一直到行尾
				while (++start < len) {
					ch = psql.charAt(start);
					if (ch == '\r' || ch == '\n') {
						break;
					}
				}
				break;
			case '-':
				ch = psql.charAt(start + 1);
				if (ch == '-') {
					// 一直到行尾
					while (++start < len) {
						ch = psql.charAt(start);
						if (ch == '\r' || ch == '\n') {
							break;
						}
					}
				}
				break;
			case '(':
				left++;
				break;
			case ')':
				left--;
				break;
			}
			start++;
		}
		return -1;
	}

}
