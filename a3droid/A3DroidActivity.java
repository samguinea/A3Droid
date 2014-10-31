package a3.a3droid;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public abstract class A3DroidActivity extends Activity implements UserInterface{

	/* Load the native alljoyn_java library. */
	static {
		System.loadLibrary("alljoyn_java");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());

		// Set new global handler
		Thread.setDefaultUncaughtExceptionHandler(new ErrorReportExceptionHandler());
	}

	// Error handler that redirects exception to the system default handler.
	public class ErrorReportExceptionHandler implements Thread.UncaughtExceptionHandler {

		//private final Thread.UncaughtExceptionHandler defaultHandler;

		public ErrorReportExceptionHandler() {
			//this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		}

		@Override
		public void uncaughtException(Thread thread, Throwable throwable) {
			Toast.makeText(getApplication(), thread + " " + throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		}
	}
}
