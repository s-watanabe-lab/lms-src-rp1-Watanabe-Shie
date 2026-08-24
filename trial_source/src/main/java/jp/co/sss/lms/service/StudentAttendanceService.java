package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */

@Service
public class StudentAttendanceService {

	//	@Autowired
	//	private TrainingTime trainingTime;
	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	//	@Autowired
	//	private MLmsUserMapper mLmsUserMapper;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			/**
			 * 渡辺志映 --Task.26 ：出退勤時刻のプルダウン初期値をセット。--
			 * DBから取得した「hh:mm」形式の文字列を、プルダウンの「時」と「分」に分割する。
			 * 既存のユーティリティ（TrainingTimeクラス）のコンストラクタに文字列を渡す。
			 */

			//出勤自国の分割
			String startTime = attendanceManagementDto.getTrainingStartTime();
			if (startTime != null && !startTime.isEmpty()) {
				TrainingTime ttStart = new TrainingTime(startTime);// Utilに分割を任せる
				dailyAttendanceForm.setTrainingStartTimeHour(ttStart.getHour());// 「時」をセット
				dailyAttendanceForm.setTrainingStartTimeMinute(ttStart.getMinute());// 「分」をセット
			}

			//退勤時間の分割
			String endTime = attendanceManagementDto.getTrainingEndTime();
			if (startTime != null && !endTime.isEmpty()) {
				TrainingTime ttEnd = new TrainingTime(endTime);// Utilに分割を任せる
				dailyAttendanceForm.setTrainingEndTimeHour(ttEnd.getHour());// 「時」をセット
				dailyAttendanceForm.setTrainingEndTimeMinute(ttEnd.getMinute());// 「分」をセット

			}
			attendanceForm.getAttendanceList().add(dailyAttendanceForm);

		}
		/**
		 *渡辺志映 --Task.26 ：画面のプルダウン用の選択肢データ（Map）作成。--
		 *keyにはInteger型の数値、valueには画面表示用のString型をセットする。
		 *Thymeleafのループ処理でこのMapを展開して<option>タグを生成する。
		 */

		//時間マップ(0～23）
		Map<Integer, String> hourMap = new LinkedHashMap<>();
		hourMap.put(null, "");
		for (int i = 0; i < 24; i++) {
			hourMap.put(i, String.format("%02d", i));
		}

		//分マップ(0～59）
		Map<Integer, String> minuteMap = new LinkedHashMap<>();
		minuteMap.put(null, "");
		for (int i = 0; i < 60; i++) {
			minuteMap.put(i, String.format("%02d", i));
		}

		attendanceForm.setHourMap(hourMap);
		attendanceForm.setMinuteMap(minuteMap);

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}

		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 渡辺志映 --Task.25：勤怠管理画面--
	 * 過去日の未入力チェック
	 * @return 未入力がある場合はtrue, ない場合はfalse
	 * @throws ParseException
	 */
	public boolean notEnterCheck() throws ParseException {

		//Service内でユーザーIDを取得する
		Integer lmsUserId = loginUserDto.getLmsUserId();

		//1.今日の日付を取得する
		Date today = new Date();

		//削除フラグ（0：未削除）
		Short deleteFlg = 0;

		//Mapperを呼び出し、未入力件数（Integer）を取得する
		Integer count = tStudentAttendanceMapper.notEnterCount(lmsUserId, deleteFlg, today);

		//件数が0より大きければ、true、そうでなければfaｌseを返す
		if (count != null && count > 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 *渡辺志映 --Task.26 ：出退勤時刻のフォーマット変換処理（更新前処理）--
	 *
	 *画面からPOSTされたプルダウンの「時」と「分」を結合し、
	 *DB更新用の「hh:mm」形式に変換してフォームオブジェクトにセットする。
	 *コントローラーのupdateメソッド内で、DB更新処理を呼ぶ直前に実行される。
	 *
	 *@param attendanceForm
	 */

	//フォーム内の「時」と「分」の入力を、「hh:mm」形式の文字列に変換してセット
	public void formatConversion(AttendanceForm attendanceForm) {

		if (attendanceForm == null || attendanceForm.getAttendanceList() == null) {
			return;
		}

		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			//出勤の「時」と「分」を結合してセット
			if (dailyAttendanceForm.getTrainingStartTimeHour() != null
					&& dailyAttendanceForm.getTrainingStartTimeMinute() != null) {

				String startTime = String.format("%02d:%02d",
						dailyAttendanceForm.getTrainingStartTimeHour(),
						dailyAttendanceForm.getTrainingStartTimeMinute());
				dailyAttendanceForm.setTrainingStartTime(startTime);
			} else {
				dailyAttendanceForm.setTrainingStartTime("");
			}

			//出勤の「時」と「分」を結合してセット
			if (dailyAttendanceForm.getTrainingEndTimeHour() != null
					&& dailyAttendanceForm.getTrainingEndTimeMinute() != null) {

				String endTime = String.format("%02d:%02d",
						dailyAttendanceForm.getTrainingEndTimeHour(),
						dailyAttendanceForm.getTrainingEndTimeMinute());
				dailyAttendanceForm.setTrainingEndTime(endTime);
			} else {
				dailyAttendanceForm.setTrainingEndTime("");
			}
		}
	}

	/**
	 * 渡辺志映 --Task.27 勤怠時間
	 * Ⅱ．入力パラメータ．勤怠リスト[n]の件数分、下記チェックを行う
	 * ａ．入力パラメータ．勤怠リスト[n]．備考の文字数　＞　100　の場合、下記エラーメッセージを追加設定
	 * メッセージID：maxlength、パラメータ："備考"、"100"
	 * 
	 * ｂ．入力パラメータ．勤怠リスト[n]．出勤時間（時）、出勤時間（分）の一方が入力有り　＆　もう一方が入力なしの場合、
	 * メッセージID：input.invalid、パラメータ："出勤時間"
	 * 
	 * ｃ．入力パラメータ．勤怠リスト[n]．退勤時間（時）、退勤時間（分）の一方が入力有り　＆　もう一方が入力なしの場合、 
	 * メッセージID：input.invalid、パラメータ："退勤時間"
	 * 
	 * ｄ．入力パラメータ．勤怠リスト[n]．出勤時間に入力なし　＆　退勤時間に入力あり　の場合、
	 * メッセージID:attendance.punchInEmpty、パラメータ：なし
	 * 
	 * ｅ．入力パラメータ．勤怠リスト[n]．出勤時間　＞　退勤時間　の場合、下記エラーメッセージを追加設定
	 * メッセージID:attendance.training.TimeRange、パラメータ：n
	 * 
	 * ｆ．入力パラメータ．勤怠リスト[n]．中抜け時間が勤務時間（出勤時間～退勤時間までの時間）を超える場合、下記エラーメッセージを追加設定
	 * メッセージID:attendance.blank.TimeError、パラメータ：なし
	 * 
	 * Ⅲ．Ⅱでエラーメッセージが設定されていた場合、下記内容を設定し勤怠情報直接変更画面へ遷移
	 * 勤怠FORM．中抜け時間（選択肢）= 勤怠Utilを使用して選択肢用の中抜け時間マップを取得
	 * 勤怠FORM．時間マップ（選択肢）= 勤怠Utilを使用して選択肢用の時間マップを取得
	 * 勤怠FORM．分マップ（選択肢）= 勤怠Utilを使用して選択肢用の分マップを取得
	 * 
	 * 画面レイアウト設計書より、
	 * コントローラー：/attendance/update
	 * パラメータ：complete
	 */

	/**
	 * 渡辺志映 --Task.27 勤怠管理直接変更画面（入力チェックの実装、ダイアログの追加）
	 * 
	 * @param attendanceForm 画面からの入力値
	 * @param result バリデーション結果
	 */
	public void updateInputCheck(AttendanceForm attendanceForm, BindingResult result) {

		List<DailyAttendanceForm> list = attendanceForm.getAttendanceList();

		for (int i = 0; i < list.size(); i++) {

			DailyAttendanceForm form = list.get(i);

			//a.備考100文字チェック
			if (form.getNote() != null && form.getNote().length() > 100) {

				//メッセージIDとパラメータを指定してエラー登録
				result.reject("maxlength", new Object[] { "備考", "100" }, null);
			}

			//b.出勤「時」と「分」の片側未入力チェック
			if ((form.getTrainingStartTimeHour() != null && form.getTrainingStartTimeMinute() == null)
					|| (form.getTrainingStartTime() == null && form.getTrainingStartTimeMinute() != null)) {
				result.reject("inout.invalid", new Object[] { "出勤時間" }, null);
			}

			//c. 出勤「時」と「分」の片側未入力チェック
			if ((form.getTrainingEndTimeHour() != null && form.getTrainingEndTimeMinute() == null)
					|| (form.getTrainingEndTime() == null && form.getTrainingEndTimeMinute() != null)) {
				result.reject("inout.invalid", new Object[] { "退勤時間" }, null);
			}

			// 出退勤入力の有無判定（時・分が揃っているか）
			boolean hasStart = (form.getTrainingStartTimeHour() != null && form.getTrainingStartTimeMinute() != null);
			boolean hasEnd = (form.getTrainingEndTimeHour() != null && form.getTrainingEndTimeMinute() != null);

			// d. 出勤時間に入力無し & 退勤時間に入力ありの場合
			if (!hasStart && hasEnd) {
				result.reject("attendance.punchInEmpty", null, null);
			}

			// 出退勤の両方が入力されている場合のみ、時間の比較計算を行う
			if (hasStart && hasEnd) {

				// 時刻を「合計分」に換算（計算しやすい数値に変換）
				TrainingTime start = new TrainingTime(form.getTrainingStartTime());
				TrainingTime end = new TrainingTime(form.getTrainingEndTime());

				int startMinutes = start.getHour() * 60 + start.getMinute();
				int endMinutes = end.getHour() * 60 + end.getMinute();

				//e.出勤時間 > 退勤時間の場合
				if (startMinutes > endMinutes) {
					result.reject("attendance.trainingTimeRange", new Object[] { i + 1 }, null);
				}

				//f.中抜け時間が勤務時間（出勤時間～退勤時間までの時間）を超える場合
				if (form.getBlankTime() != null) {
					int workMinutes = endMinutes - startMinutes; // 実際の勤務時間（分）
					if (form.getBlankTime() > workMinutes) {
						result.reject("attendance.blankTimeError", null, null);
					}

				}

			}

		}

		//エラーが1件でも設定されていた場合、プルダウン用マップを再設定
		if (result.hasErrors()) {
			attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

			// 時間マップ（0～23）
			Map<Integer, String> hourMap = new LinkedHashMap<>();
			hourMap.put(null, "");

			for (int h = 0; h < 24; h++) {
				hourMap.put(h, String.format("%02d", h));
			}

			// 分マップ（0～59）
			Map<Integer, String> minuteMap = new LinkedHashMap<>();
			minuteMap.put(null, "");
			for (int m = 0; m < 60; m++) {
				minuteMap.put(m, String.format("%02d", m));
			}

			attendanceForm.setHourMap(hourMap);
			attendanceForm.setMinuteMap(minuteMap);
		}
	}
}