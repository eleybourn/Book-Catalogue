package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Intent;

public class BuiltinScanner implements Scanner {

   @Override
   public void startActivityForResult(Activity a, int requestCode) {
      Intent i = new Intent(a, ScannerActivity.class);
      a.startActivityForResult(i, requestCode);
   }

   @Override
   public String getBarcode(Intent intent) {
      return ScannerActivity.getIsbn(intent);
   }
}
