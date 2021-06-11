package VASSAL.build.module.dice;

import VASSAL.build.GameModule;
import VASSAL.build.module.DiceButton;
import VASSAL.build.module.DieRoll;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.InternetDiceButton;
import VASSAL.script.expression.Auditable;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.FormattedString;
import VASSAL.tools.ProblemDialog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base DieServer Class
 * Does most of the work. Individual Die Servers just need to implement
 * {@link #buildInternetRollString} and {@link #parseInternetRollString}
 * methods.
 */
public abstract class DieServer implements Auditable {
  private static final Logger logger = LoggerFactory.getLogger(DieServer.class);

  protected java.util.Random ran;
  protected String name;
  protected String description;
  protected boolean emailOnly;
  protected int maxRolls;
  protected int maxEmails;
  protected String serverURL;
  protected boolean passwdRequired = false;
  protected String password = "";  //NON-NLS
  protected boolean useEmail;
  protected String primaryEmail;
  protected String secondaryEmail;
  protected boolean canDoSeparateDice = false;

  /*
   * Each implemented die server must provide this routine to build a
   * string that will be sent to the internet site to drive the web-based
   * die server. This will usually be a control string passed to a cgi script
   * on the site.
   */
  public abstract String[] buildInternetRollString(RollSet mr);

  /*
   * Each implemented die server must provide this routine to interpret the
   * html output generated by the site in response to the
   * {@link #buildInternetRollString} call.
   */
  public abstract void parseInternetRollString(RollSet rollSet, Vector<String> results); //NOPMD

  /*
   * Internet Die Servers should always implement roll by calling back to
   * {@link #doInternetRoll}
   */
  public abstract void roll(RollSet mr, FormattedString format);

  public DieServer() {
    ran = GameModule.getGameModule().getRNG();
  }

  /*
   * Some Internet servers can only roll specific numbers of dice or
   * dice with specific sides. These are the default settings.
   */
  public int[] getnDiceList() {
    return new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
  }

  public int[] getnSideList() {
    return new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 20, 30, 50, 100, 1000};
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean isPasswdRequired() {
    return passwdRequired;
  }

  public String getPasswd() {
    return password;
  }

  public void setPasswd(String s) {
    password = s;
  }

  public void setPrimaryEmail(String e) {
    primaryEmail = e;
  }

  public String getPrimaryEmail() {
    return primaryEmail;
  }

  public void setSecondaryEmail(String e) {
    secondaryEmail = e;
  }

  public String getSecondaryEmail() {
    return secondaryEmail;
  }

  public void setUseEmail(boolean use) {
    useEmail = use;
  }

  public boolean getUseEmail() {
    return useEmail;
  }

  public int getMaxEmails() {
    return maxEmails;
  }

  /**
   * The text reported before the results of the roll
   */
  protected String getReportPrefix(String d) {
    return " *** " + d + " = "; //NON-NLS
  }

  /**
   * The text reported after the results of the roll;
   * @deprecated No Replacement, handled by Message format
   */
  @Deprecated(since = "2020-08-06", forRemoval = true)
  protected String getReportSuffix() {
    ProblemDialog.showDeprecated("2020-08-06");  //NON-NLS
    return " ***  &lt;" + GlobalOptions.getInstance().getPlayerId() + "&gt;";  //NON-NLS
  }

  /*
   * Called by the Inbuilt server - Basically the same as the code
   * in the original DiceButton
   */
  @Deprecated(since = "2020-08-06", forRemoval = true)
  public void doInbuiltRoll(RollSet mroll) {
    ProblemDialog.showDeprecated("2020-08-06"); //NON-NLS
    final DieRoll[] rolls = mroll.getDieRolls();
    for (final DieRoll roll : rolls) {
      final String desc = roll.getDescription();
      final int nSides = roll.getNumSides();
      final int nDice = roll.getNumDice();
      final int plus = roll.getPlus();
      final boolean reportTotal = roll.isReportTotal();

      String val = getReportPrefix(desc);
      int total = 0;
      for (int j = 0; j < nDice; ++j) {
        final int result = ran.nextInt(nSides) + 1 + plus;
        if (reportTotal) {
          total += result;
        }
        else {
          val += result;
          if (j < nDice - 1)
            val += ",";
        }

        if (reportTotal)
          val += total;

        final String reportSuffix = " ***  &lt;" + GlobalOptions.getInstance().getPlayerId() + "&gt;"; //NON-NLS
        val += reportSuffix;
        GameModule.getGameModule().getChatter().send(val);
      }
    }
  }

  /*
   * Internet Servers will call this routine to do their dirty work.
   */
  public void doInternetRoll(final RollSet mroll, final FormattedString format) {
    // FIXME: refactor so that doInBackground can return something useful
    new SwingWorker<Void, Void>() {
      @Override
      public Void doInBackground() throws Exception {
        doIRoll(mroll);
        return null;
      }

      @Override
      protected void done() {
        try {
          get();
          reportResult(mroll, format);
        }
        catch (InterruptedException e) {
          ErrorDialog.bug(e);
        }
        // FIXME: review error message
        catch (ExecutionException e) {
          logger.error("", e);

          final String s = "- Internet dice roll attempt " + //NON-NLS
                           mroll.getDescription() + " failed."; //NON-NLS
          GameModule.getGameModule().getChatter().send(s);
        }
      }
    }.execute();
  }

  /**
   * Use the configured FormattedString to format the result of a roll
   * @param description Roll Description
   * @param result Roll Result
   * @param format Report Format
   * @return Formatted roll result
   */
  protected String formatResult(String description, String result, FormattedString format) {
    format.setProperty(DiceButton.RESULT, result);
    format.setProperty(InternetDiceButton.DETAILS, description);
    final String text = format.getText(this, "Editor.report_format");
    return text.startsWith("*") ? "*" + text : "* " + text;
  }


  public void reportResult(RollSet mroll, FormattedString format) {
    final DieRoll[] rolls = mroll.getDieRolls();
    for (final DieRoll roll : rolls) {
      final int nDice = roll.getNumDice();
      final boolean reportTotal = roll.isReportTotal();

      final StringBuilder val = new StringBuilder();
      int total = 0;

      for (int j = 0; j < nDice; j++) {
        final int result = roll.getResult(j);
        if (reportTotal) {
          total += result;
        }
        else {
          val.append(result);
          if (j < nDice - 1)
            val.append(',');
        }
      }

      if (reportTotal)
        val.append(total);

      GameModule.getGameModule().getChatter().send(formatResult(roll.getDescription(), val.toString(), format));
    }
  }

  public void doIRoll(RollSet toss) throws IOException {
    final String[] rollString = buildInternetRollString(toss);
    final ArrayList<String> returnString = new ArrayList<>();
    //            rollString[0] =
    //                "number1=2&type1=6&number2=2&type2=30&number3=2&type3=30"
    //                    + "&number4=0&type4=2&number5=0&type5=2&number6=0&type6=2&number7=0&type7=2"
    //                    + "&number8=0&type8=2&number9=0&type9=2&number10=0&type10=2"
    //                    + "&emails=&email=b.easton@uws.edu.au&password=IG42506&Submit=Throw+Dice";
    final URL url = new URL(serverURL);

    final URLConnection connection = url.openConnection();
    connection.setDoOutput(true);

    try (OutputStream os = connection.getOutputStream();
         PrintWriter out = new PrintWriter(os, true, StandardCharsets.UTF_8)) {
      for (final String s : rollString) {
        out.println(s);
      }
    }

    try (InputStream is = connection.getInputStream();
         InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
         BufferedReader in = new BufferedReader(isr)) {
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        returnString.add(inputLine);
      }
    }

    parseInternetRollString(toss, new Vector<>(returnString));
  }

  /*
   * Extract the portion of the email address withing the  angle brackets.
   * Allows Email addresses like 'Joe Blow <j.blow@somewhere.com>'
   */
  public String extractEmail(String email) {
    final int start = email.indexOf('<');
    final int end = email.indexOf('>');
    if (start >= 0 && end >= 0 && end > start) {
      return email.substring(start + 1, end);
    }
    else {
      return email;
    }
  }
}
