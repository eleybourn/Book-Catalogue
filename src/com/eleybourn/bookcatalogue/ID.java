package com.eleybourn.bookcatalogue;

public class ID {
	private static int CNT = 0;
	public static final int TASK_ID_SAVE = ++CNT;
	public static final int TASK_ID_OPEN = ++CNT;
	public static final int DIALOG_OPEN_IMPORT_TYPE = ++CNT;
	public static final int MSG_ID_BACKUP_EXPORT_COMPLETE = ++CNT;
	public static final int MSG_ID_BACKUP_IMPORT_COMPLETE = ++CNT;
}
