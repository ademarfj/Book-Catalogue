package com.eleybourn.bookcatalogue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Message;
import android.widget.Toast;

/**
 * Class to handle export in a separate thread.
 * 
 * @author Grunthos
 */
public class ExportThread extends ManagedTask {
	private static String mFilePath = Utils.EXTERNAL_FILE_PATH;
	private static String mFileName = mFilePath + "/export.csv";
	private static String UTF8 = "utf8";
	private static int BUFFER_SIZE = 8192;
	private CatalogueDBAdapter mDbHelper;
	private Context context;

	public interface ExportHandler extends ManagedTask.TaskHandler {
		void onFinish();
	}

	public ExportThread(TaskManager ctx, ExportHandler taskHandler, AdministrationFunctions thisContext) {
		super(ctx, taskHandler);
		context = thisContext;
		mDbHelper = new CatalogueDBAdapter(ctx.getAppContext());
		mDbHelper.open();

	}

	@Override
	protected boolean onFinish() {
		AlertDialog alertDialog = new AlertDialog.Builder(context).create();
		alertDialog.setTitle(R.string.email_export);
		alertDialog.setIcon(android.R.drawable.ic_menu_send);
		alertDialog.setButton(context.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// setup the mail message
				final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
				emailIntent.setType("plain/text");
				//emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, context.getString(R.string.debug_email).split(";"));
				String subject = "[" + context.getString(R.string.app_name) + "] " + context.getString(R.string.export_data);
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
				//emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.debug_body));
				//has to be an ArrayList
				ArrayList<Uri> uris = new ArrayList<Uri>();
				// Find all files of interest to send
				try {
					File fileIn = new File(Utils.EXTERNAL_FILE_PATH + "/" + "export.csv");
					Uri u = Uri.fromFile(fileIn);
					uris.add(u);
					// Send it, if there are any files to send.
					emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
					context.startActivity(Intent.createChooser(emailIntent, "Send mail..."));        	
				} catch (NullPointerException e) {
					Logger.logError(e);
					Toast.makeText(context, R.string.export_failed_sdcard, Toast.LENGTH_LONG).show();
				}

				return;
			}
		}); 
		alertDialog.setButton2(context.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				//do nothing
				return;
			}
		}); 
		alertDialog.show();

		
		try {
			ExportHandler h = (ExportHandler)getTaskHandler();
			if (h != null) {
				h.onFinish();
				return true;
			} else {
				return false;
			}
		} finally {
			cleanup();
		}
	}
	
	@Override
	protected void onMessage(Message msg) {
		// Nothing to do. we don't sent any
	}
	
	@Override
	protected void onRun() {
		int num = 0;
		if (!Utils.sdCardWritable()) {
			mManager.doToast("Export Failed - Could not write to SDCard");
			return;			
		}
		
		StringBuilder export = new StringBuilder(
			'"' + CatalogueDBAdapter.KEY_ROWID + "\"," + 			//0
			'"' + CatalogueDBAdapter.KEY_AUTHOR_DETAILS + "\"," + 	//2
			'"' + CatalogueDBAdapter.KEY_TITLE + "\"," + 			//4
			'"' + CatalogueDBAdapter.KEY_ISBN + "\"," + 			//5
			'"' + CatalogueDBAdapter.KEY_PUBLISHER + "\"," + 		//6
			'"' + CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\"," + 	//7
			'"' + CatalogueDBAdapter.KEY_RATING + "\"," + 			//8
			'"' + "bookshelf_id\"," + 								//9
			'"' + CatalogueDBAdapter.KEY_BOOKSHELF + "\"," +		//10
			'"' + CatalogueDBAdapter.KEY_READ + "\"," +				//11
			'"' + CatalogueDBAdapter.KEY_SERIES_DETAILS + "\"," +	//12
			'"' + CatalogueDBAdapter.KEY_PAGES + "\"," + 			//14
			'"' + CatalogueDBAdapter.KEY_NOTES + "\"," + 			//15
			'"' + CatalogueDBAdapter.KEY_LIST_PRICE + "\"," + 		//16
			'"' + CatalogueDBAdapter.KEY_ANTHOLOGY+ "\"," + 		//17
			'"' + CatalogueDBAdapter.KEY_LOCATION+ "\"," + 			//18
			'"' + CatalogueDBAdapter.KEY_READ_START+ "\"," + 		//19
			'"' + CatalogueDBAdapter.KEY_READ_END+ "\"," + 			//20
			'"' + CatalogueDBAdapter.KEY_FORMAT+ "\"," + 			//21
			'"' + CatalogueDBAdapter.KEY_SIGNED+ "\"," + 			//22
			'"' + CatalogueDBAdapter.KEY_LOANED_TO+ "\"," +			//23 
			'"' + "anthology_titles" + "\"," +						//24 
			'"' + CatalogueDBAdapter.KEY_DESCRIPTION+ "\"," + 		//25
			'"' + CatalogueDBAdapter.KEY_GENRE+ "\"," + 			//26
			"\n");
		
		long lastUpdate = 0;
		
		StringBuilder row = new StringBuilder();
		
		Cursor books = mDbHelper.exportBooks();
		mManager.setMax(this, books.getCount());
		
		try {
			/* write to the SDCard */
			backupExport();
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mFileName), UTF8), BUFFER_SIZE);
			out.write(export.toString());
			if (books.moveToFirst()) {
				do { 
					num++;
					long id = books.getLong(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
					String dateString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1]);
						int dd = Integer.parseInt(date[2]);
						dateString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String dateReadStartString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1]);
						int dd = Integer.parseInt(date[2]);
						dateReadStartString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String dateReadEndString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1]);
						int dd = Integer.parseInt(date[2]);
						dateReadEndString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String anthology = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
					String anthology_titles = "";
					if (anthology.equals(CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS + "") || anthology.equals(CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR + "")) {
						Cursor titles = mDbHelper.fetchAnthologyTitlesByBook(id);
						try {
							if (titles.moveToFirst()) {
								do { 
									String anth_title = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
									String anth_author = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR_NAME));
									anthology_titles += anth_title + " * " + anth_author + "|";
								} while (titles.moveToNext()); 
							}
						} finally {
							if (titles != null)
								titles.close();
						}
					}
					String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
					//Display the selected bookshelves
					Cursor bookshelves = mDbHelper.fetchAllBookshelvesByBook(id);
					String bookshelves_id_text = "";
					String bookshelves_name_text = "";
					while (bookshelves.moveToNext()) {
						bookshelves_id_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_ROWID)) + BookEditFields.BOOKSHELF_SEPERATOR;
						bookshelves_name_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BookEditFields.BOOKSHELF_SEPERATOR;
					}
					bookshelves.close();
					
					String authorDetails = Utils.getAuthorUtils().encodeList( mDbHelper.getBookAuthorList(id), '|' );
					String seriesDetails = Utils.getSeriesUtils().encodeList( mDbHelper.getBookSeriesList(id), '|' );
					
					row.setLength(0);
					row.append("\"" + formatCell(id) + "\",");
					row.append("\"" + formatCell(authorDetails) + "\",");
					row.append( "\"" + formatCell(title) + "\"," );
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN))) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER))) + "\",");
					row.append("\"" + formatCell(dateString) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING))) + "\",");
					row.append("\"" + formatCell(bookshelves_id_text) + "\",");
					row.append("\"" + formatCell(bookshelves_name_text) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ))) + "\",");
					row.append("\"" + formatCell(seriesDetails) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES))) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES))) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE))) + "\",");
					row.append("\"" + formatCell(anthology) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION))) + "\",");
					row.append("\"" + formatCell(dateReadStartString) + "\",");
					row.append("\"" + formatCell(dateReadEndString) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FORMAT))) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED))) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO))+"") + "\",");
					row.append("\"" + formatCell(anthology_titles) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DESCRIPTION))) + "\",");
					row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GENRE))) + "\",");
					row.append("\n");
					out.write(row.toString());
					//export.append(row);
					
					long now = System.currentTimeMillis();
					if ( (now - lastUpdate) > 200) {
						doProgress(title, num);
						lastUpdate = now;
					}
				}
				while (books.moveToNext() && !isCancelled()); 
			} 
			
			out.close();
			//Toast.makeText(AdministrationFunctions.this, R.string.export_complete, Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			Logger.logError(e);
			mManager.doToast(getString(R.string.export_failed_sdcard));
		} finally {
			mManager.doToast( getString(R.string.export_complete) );
			if (books != null)
				books.close();
		}
	}
	
	/**
	 * Backup the current file
	 */
	private void backupExport() {
		File export = new File(mFileName);
		File backup = new File(mFileName + ".bak");
		export.renameTo(backup);
	}
	
	/**
	 * Double quote all "'s and remove all newlines
	 * 
	 * @param cell The cell the format
	 * @return The formatted cell
	 */
	private String formatCell(String cell) {
		try {
			if (cell.equals("null") || cell.trim().length() == 0) {
				return "";
			}
			StringBuilder bld = new StringBuilder();
			int endPos = cell.length() - 1;
			int pos = 0;
			while (pos <= endPos) {
				char c = cell.charAt(pos);
				switch(c) {
				case '\r':
					bld.append("\\r");
					break;
				case '\n':
					bld.append("\\n");
					break;
				case '\t':
					bld.append("\\t");
					break;
				case '"':
					bld.append("\"\"");
					break;
				case '\\':
					bld.append("\\\\");
					break;
				default:
					bld.append(c);
				}
				pos++;

			}
			return bld.toString();
		} catch (NullPointerException e) {
			return "";
		}
	}
	
	/**
	 * @see formatCell(String cell)
	 * @param cell The cell the format
	 * @return The formatted cell
	 */
	private String formatCell(long cell) {
		String newcell = cell + "";
		return formatCell(newcell);
	}

	/**
	 * Cleanup any DB connection etc after main task has run.
	 */
	private void cleanup() {
		if (mDbHelper != null) {
			mDbHelper.close();
			mDbHelper = null;
		}		
	}

	@Override
	protected void finalize() throws Throwable {
		cleanup();
		super.finalize();
	}
}
