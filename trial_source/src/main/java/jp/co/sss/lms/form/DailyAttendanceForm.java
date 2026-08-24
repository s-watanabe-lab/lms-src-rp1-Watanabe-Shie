package jp.co.sss.lms.form;

import lombok.Data;

/**
 * 日次の勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class DailyAttendanceForm {

	/** 受講生勤怠ID */
	private Integer studentAttendanceId;
	/** 途中退校日 */
	private String leaveDate;
	/** 日付 */
	private String trainingDate;
	/** 出勤時間 */
	private String trainingStartTime;
	
	//Task.26 出退勤「時」と「分」
	/** 出勤時間（時）*/
	private Integer trainingStartTimeHour;
	/** 出勤時間（分）*/
	private Integer trainingStartTimeMinute;
	/** 退勤時間（時）*/
	private Integer trainingEndTimeHour;
	/** 退勤時間（分）*/
	private Integer trainingEndTimeMinute;
	
	//Task.27 入力チェック
	//出勤時間の未入力・退勤時間の未入力
	//DailyAttendanceFormごとにチェックを実施
	//時刻の「時」だけと、「分」だけ
	//「出勤なし、退勤あり」の矛盾チェック
	//[if エラーがなければ]出勤時刻 > 退勤時刻になってないかの比較チェック
	//[if 中抜け時間が入力されている場合]出勤時の差分から計算される最大受講時間よりも中抜け時間が長くないかチェック
	
	/** 退勤時間 */
	private String trainingEndTime;
	/** 中抜け時間 */
	private Integer blankTime;
	/** 中抜け時間（画面表示用） */
	private String blankTimeValue;
	/** ステータス */
	private String status;
	/** 備考 */
	private String note;
	/** セクション名 */
	private String sectionName;
	/** 当日フラグ */
	private Boolean isToday;
	/** エラーフラグ */
	private Boolean isError;
	/** 日付（画面表示用） */
	private String dispTrainingDate;
	/** ステータス（画面表示用） */
	private String statusDispName;
	/** LMSユーザーID */
	private String lmsUserId;
	/** ユーザー名 */
	private String userName;
	/** コース名 */
	private String courseName;
	/** インデックス */
	private String index;

}
