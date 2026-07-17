package com.faithlog.devotion.service.export;

import com.faithlog.devotion.service.result.AdminWeeklyDevotionResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class AdminWeeklyDevotionExcelRenderer {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter SUBMITTED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		.withZone(SEOUL_ZONE);
	private static final String[] SUMMARY_HEADERS = {
		"사용자 ID", "이름", "이메일", "큐티", "성경", "기도", "토요일 지각(분)", "제출 시각", "벌금 청구 ID", "벌금 금액", "벌금 상태"
	};
	private static final String[] DAILY_HEADERS = {
		"구분", "사용자 ID", "이름", "이메일", "날짜", "요일", "큐티", "성경", "기도"
	};
	private static final Map<DayOfWeek, String> KOREAN_DAY_NAMES = Map.of(
		DayOfWeek.MONDAY, "월",
		DayOfWeek.TUESDAY, "화",
		DayOfWeek.WEDNESDAY, "수",
		DayOfWeek.THURSDAY, "목",
		DayOfWeek.FRIDAY, "금",
		DayOfWeek.SATURDAY, "토",
		DayOfWeek.SUNDAY, "일"
	);

	public byte[] render(AdminWeeklyDevotionResult result) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			CellStyle headerStyle = headerStyle(workbook);
			CellStyle sectionStyle = sectionStyle(workbook);
			writeSummarySheet(workbook, result, headerStyle, sectionStyle);
			writeDailySheet(workbook, result, headerStyle, sectionStyle);
			workbook.write(output);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to render weekly devotion workbook.", exception);
		}
	}

	private void writeSummarySheet(
		Workbook workbook,
		AdminWeeklyDevotionResult result,
		CellStyle headerStyle,
		CellStyle sectionStyle
	) {
		Sheet sheet = workbook.createSheet("주간 요약");
		writeLabelValue(sheet.createRow(0), "주차", result.weekStartDate() + " ~ " + result.weekEndDate());
		writeLabelValue(sheet.createRow(1), "활성 멤버", result.activeMemberCount());
		writeLabelValue(sheet.createRow(2), "제출", result.submittedCount());
		writeLabelValue(sheet.createRow(3), "미제출", result.missingCount());
		writeLabelValue(sheet.createRow(4), "벌금 총액", result.totalPenaltyAmount());

		Row submittedSection = sheet.createRow(6);
		setString(submittedSection, 0, "제출자").setCellStyle(sectionStyle);
		writeHeaders(sheet.createRow(7), SUMMARY_HEADERS, headerStyle);
		int rowIndex = 8;
		for (AdminWeeklyDevotionResult.SubmittedMember member : result.submittedMembers()) {
			writeSubmittedSummaryRow(sheet.createRow(rowIndex++), member);
		}

		rowIndex++;
		Row missingSection = sheet.createRow(rowIndex++);
		setString(missingSection, 0, "미제출자").setCellStyle(sectionStyle);
		writeHeaders(sheet.createRow(rowIndex++), SUMMARY_HEADERS, headerStyle);
		for (AdminWeeklyDevotionResult.MissingMember member : result.missingMembers()) {
			Row row = sheet.createRow(rowIndex++);
			setNumber(row, 0, member.userId());
			setString(row, 1, member.name());
			setString(row, 2, member.email());
		}
		setSummaryColumnWidths(sheet);
	}

	private void writeDailySheet(
		Workbook workbook,
		AdminWeeklyDevotionResult result,
		CellStyle headerStyle,
		CellStyle sectionStyle
	) {
		Sheet sheet = workbook.createSheet("일별 상세");
		writeHeaders(sheet.createRow(0), DAILY_HEADERS, headerStyle);
		int rowIndex = 1;
		for (AdminWeeklyDevotionResult.SubmittedMember member : result.submittedMembers()) {
			for (AdminWeeklyDevotionResult.DailyCheck dailyCheck : member.dailyChecks()) {
				Row row = sheet.createRow(rowIndex++);
				setString(row, 0, "제출");
				setNumber(row, 1, member.userId());
				setString(row, 2, member.name());
				setString(row, 3, member.email());
				setString(row, 4, dailyCheck.recordDate().toString());
				setString(row, 5, KOREAN_DAY_NAMES.get(dailyCheck.recordDate().getDayOfWeek()));
				setString(row, 6, yesNo(dailyCheck.quietTimeChecked()));
				setString(row, 7, yesNo(dailyCheck.bibleReadingChecked()));
				setString(row, 8, yesNo(dailyCheck.prayerChecked()));
			}
		}

		rowIndex++;
		Row missingSection = sheet.createRow(rowIndex++);
		setString(missingSection, 0, "미제출자").setCellStyle(sectionStyle);
		writeHeaders(sheet.createRow(rowIndex++), DAILY_HEADERS, headerStyle);
		for (AdminWeeklyDevotionResult.MissingMember member : result.missingMembers()) {
			Row row = sheet.createRow(rowIndex++);
			setString(row, 0, "미제출");
			setNumber(row, 1, member.userId());
			setString(row, 2, member.name());
			setString(row, 3, member.email());
		}
		setDailyColumnWidths(sheet);
	}

	private void writeSubmittedSummaryRow(Row row, AdminWeeklyDevotionResult.SubmittedMember member) {
		setNumber(row, 0, member.userId());
		setString(row, 1, member.name());
		setString(row, 2, member.email());
		setNumber(row, 3, member.quietTimeCount());
		setNumber(row, 4, member.bibleReadingCount());
		setNumber(row, 5, member.prayerCount());
		setNumber(row, 6, member.saturdayLateMinutes());
		setString(row, 7, SUBMITTED_AT_FORMAT.format(member.submittedAt()));
		if (member.penalty() != null) {
			setNumber(row, 8, member.penalty().chargeItemId());
			setNumber(row, 9, member.penalty().amount());
			setString(row, 10, member.penalty().status().name());
		}
	}

	private void writeHeaders(Row row, String[] headers, CellStyle style) {
		for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
			Cell cell = setString(row, columnIndex, headers[columnIndex]);
			cell.setCellStyle(style);
		}
	}

	private void writeLabelValue(Row row, String label, String value) {
		setString(row, 0, label);
		setString(row, 1, value);
	}

	private void writeLabelValue(Row row, String label, long value) {
		setString(row, 0, label);
		setNumber(row, 1, value);
	}

	private Cell setString(Row row, int columnIndex, String value) {
		Cell cell = row.createCell(columnIndex);
		cell.setCellValue(value == null ? "" : value);
		return cell;
	}

	private void setNumber(Row row, int columnIndex, long value) {
		row.createCell(columnIndex).setCellValue(value);
	}

	private String yesNo(boolean checked) {
		return checked ? "Y" : "N";
	}

	private void setSummaryColumnWidths(Sheet sheet) {
		int[] widths = {14, 18, 32, 10, 10, 10, 18, 22, 18, 14, 14};
		setColumnWidths(sheet, widths);
	}

	private void setDailyColumnWidths(Sheet sheet) {
		int[] widths = {12, 14, 18, 32, 14, 8, 10, 10, 10};
		setColumnWidths(sheet, widths);
	}

	private void setColumnWidths(Sheet sheet, int[] widths) {
		for (int columnIndex = 0; columnIndex < widths.length; columnIndex++) {
			sheet.setColumnWidth(columnIndex, widths[columnIndex] * 256);
		}
	}

	private CellStyle headerStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}

	private CellStyle sectionStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		return style;
	}
}
