package com.jasonfc;

import java.util.HashMap;

import com.jasonfc.BookProviderMetaData.BookTableMetaData;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class BookProvider extends ContentProvider {

	private static final String TAG = "BookProvider";

	private static HashMap<String, String> sBookProjectionMap;
	static {
		sBookProjectionMap = new HashMap<String, String>();
		sBookProjectionMap.put(BookTableMetaData._ID, BookTableMetaData._ID);

		sBookProjectionMap.put(BookTableMetaData.BOOK_NAME, BookTableMetaData.BOOK_NAME);
		sBookProjectionMap.put(BookTableMetaData.BOOK_ISBN, BookTableMetaData.BOOK_ISBN);
		sBookProjectionMap.put(BookTableMetaData.BOOK_AUTHOR, BookTableMetaData.BOOK_AUTHOR);

		sBookProjectionMap.put(BookTableMetaData.CREATE_DATE, BookTableMetaData.CREATE_DATE);
		sBookProjectionMap.put(BookTableMetaData.MODIFIED_DATE, BookTableMetaData.MODIFIED_DATE);

	}

	private static final UriMatcher sUrimatcher;
	private static final int INCOMING_BOOK_COLLECTION_URI_INDICATOR = 1;
	private static final int INCOMING_SINGLE_BOOK_URI_INDICATOR = 2;
	static {
		sUrimatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUrimatcher.addURI(BookProviderMetaData.AUTHPRITY, "books", INCOMING_BOOK_COLLECTION_URI_INDICATOR);
		sUrimatcher.addURI(BookProviderMetaData.AUTHPRITY, "books/#", INCOMING_SINGLE_BOOK_URI_INDICATOR);
	}

	private static class DatabaseHelper extends SQLiteOpenHelper {

		public DatabaseHelper(Context context) {
			super(context, BookProviderMetaData.DATABASE_NAME, null, BookProviderMetaData.DATABASE_VERSION);
		}

		public void onCreate(SQLiteDatabase db) {
			Log.d(TAG, "inner oncreate called");
			db.execSQL("CREATE TABLE " + BookTableMetaData.TABLE_NAME + " (" + BookTableMetaData._ID
					+ " INTEGER PRIMARY KEY," + BookTableMetaData.BOOK_NAME + " TEXT," + BookTableMetaData.BOOK_ISBN
					+ " TEXT," + BookTableMetaData.BOOK_AUTHOR + " TEXT," + BookTableMetaData.CREATE_DATE + " INTEGER,"
					+ BookTableMetaData.MODIFIED_DATE + " INTEGER" + ");");

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.d(TAG, "inner onupgrade called");
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion
					+ ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + BookTableMetaData.TABLE_NAME);
			onCreate(db);
		}
	}

	private DatabaseHelper mOpenHelper;

	@Override
	public boolean onCreate() {
		Log.d(TAG, "main onCreate called");
		mOpenHelper = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		switch (sUrimatcher.match(uri)) {
		case INCOMING_BOOK_COLLECTION_URI_INDICATOR:
			qb.setTables(BookTableMetaData.TABLE_NAME);
			qb.setProjectionMap(sBookProjectionMap);
			break;
		case INCOMING_SINGLE_BOOK_URI_INDICATOR:
			qb.setTables(BookTableMetaData.TABLE_NAME);
			qb.setProjectionMap(sBookProjectionMap);
			qb.appendWhere(BookTableMetaData._ID + "=" + uri.getPathSegments().get(1));
			break;
		default:
			throw new IllegalArgumentException("Unknown URI" + uri);
		}

		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = BookTableMetaData.DEFAULT_SORT_ORDER;

		} else {
			orderBy = sortOrder;
		}

		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
		int i = c.getCount();
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch (sUrimatcher.match(uri)) {
		case INCOMING_BOOK_COLLECTION_URI_INDICATOR:
			count = db.delete(BookTableMetaData.TABLE_NAME, where, whereArgs);
			break;

		case INCOMING_SINGLE_BOOK_URI_INDICATOR:
			String rowId = uri.getPathSegments().get(1);
			count = db.delete(BookTableMetaData.TABLE_NAME,
					BookTableMetaData._ID + "=" + rowId + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
					whereArgs);
			break;

		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUrimatcher.match(uri)) {
		case INCOMING_BOOK_COLLECTION_URI_INDICATOR:
			return BookTableMetaData.CONTENT_TYPE;

		case INCOMING_SINGLE_BOOK_URI_INDICATOR:
			return BookTableMetaData.CONTENT_ITEM_TYPE;

		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {

		// Validate the requested uri
		if (sUrimatcher.match(uri) != INCOMING_BOOK_COLLECTION_URI_INDICATOR) {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		ContentValues values;
		if (initialValues != null) {
			values = new ContentValues(initialValues);
		} else {
			values = new ContentValues();
		}

		Long now = Long.valueOf(System.currentTimeMillis());

		// Make sure that the fields are all set
		if (values.containsKey(BookTableMetaData.CREATE_DATE) == false) {
			values.put(BookTableMetaData.CREATE_DATE, now);
		}

		if (values.containsKey(BookTableMetaData.MODIFIED_DATE) == false) {
			values.put(BookTableMetaData.MODIFIED_DATE, now);
		}

		if (values.containsKey(BookTableMetaData.BOOK_NAME) == false) {
			throw new SQLException("Failed to insert row because Book Name is needed " + uri);
		}

		if (values.containsKey(BookTableMetaData.BOOK_ISBN) == false) {
			values.put(BookTableMetaData.BOOK_ISBN, "Unknown ISBN");
		}
		if (values.containsKey(BookTableMetaData.BOOK_AUTHOR) == false) {
			values.put(BookTableMetaData.BOOK_ISBN, "Unknown Author");
		}

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		long rowId = db.insert(BookTableMetaData.TABLE_NAME, BookTableMetaData.BOOK_NAME, values);
		if (rowId > 0) {
			Uri insertedBookUri = ContentUris.withAppendedId(BookTableMetaData.CONTEBT_URI, rowId);
			getContext().getContentResolver().notifyChange(insertedBookUri, null);

			return insertedBookUri;
		}

		throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch (sUrimatcher.match(uri)) {
		case INCOMING_BOOK_COLLECTION_URI_INDICATOR:
			count = db.update(BookTableMetaData.TABLE_NAME, values, where, whereArgs);
			break;

		case INCOMING_SINGLE_BOOK_URI_INDICATOR:
			String rowId = uri.getPathSegments().get(1);
			count = db.update(BookTableMetaData.TABLE_NAME, values,
					BookTableMetaData._ID + "=" + rowId + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
					whereArgs);
			break;

		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

}
