package de.mkrtchyan.recoverytools;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatTextView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.fima.cardsui.objects.Card;
import com.fima.cardsui.objects.CardColorScheme;
import com.fima.cardsui.views.CardUI;
import com.fima.cardsui.views.IconCard;
import com.fima.cardsui.views.SimpleCard;

import org.sufficientlysecure.rootcommands.Toolbox;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;

import de.mkrtchyan.utils.Common;
import de.mkrtchyan.utils.Downloader;
import de.mkrtchyan.utils.FileChooserDialog;

/**
 * Copyright (c) 2015 Aschot Mkrtchyan
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class FlashFragment extends Fragment {

    private SwipeRefreshLayout mSwipeUpdater = null;
    private File RecoveryCollectionFile, KernelCollectionFile;

    private Device mDevice;
    private Toolbox mToolbox;
    private Context mContext;
    private RashrActivity mActivity;
    private boolean isRecoveryListUpToDate = true;
    private boolean isKernelListUpToDate = true;

    public static FlashFragment newInstance(RashrActivity activity) {
        FlashFragment fragment = new FlashFragment();
        fragment.setActivity(activity);
        fragment.setDevice(activity.getDevice());
        fragment.setToolbox(activity.getToolbox());
        return fragment;
    }

    public FlashFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecoveryCollectionFile = new File(mContext.getFilesDir(), "recovery_sums");
        KernelCollectionFile = new File(mContext.getFilesDir(), "kernel_sums");
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        /**
         * Backups menu only accessible if backups are possible
         */
        if (mDevice.isRecoveryDD() || mDevice.isKernelDD()
                || mDevice.isRecoveryMTD() || mDevice.isKernelMTD())
            inflater.inflate(R.menu.flash_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_rashr, container, false);
        /** Check if device uses unified builds */
        if (Common.getBooleanPref(mContext, Constants.PREF_NAME, Constants.PREF_KEY_SHOW_UNIFIED)
                && mDevice.isUnified()
                && (!mDevice.isStockRecoverySupported() || !mDevice.isCwmRecoverySupported()
                || !mDevice.isTwrpRecoverySupported() || !mDevice.isPhilzRecoverySupported())) {
            showUnifiedBuildsDialog();
        }
        optimizeLayout(root);
        root.setBackgroundColor(
                RashrActivity.isDark ? getResources().getColor(R.color.background_material_dark) :
                getResources().getColor(R.color.background_material_light));
        if (Common.getBooleanPref(mContext, Constants.PREF_NAME, Constants.PREF_KEY_CHECK_UPDATES)
                && RashrActivity.FirstSession) {
            catchUpdates(true);
            RashrActivity.FirstSession = false;
        }
        return root;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.BackupItem:
                mActivity.switchTo(BackupRestoreFragment.newInstance(mActivity));
                break;
        }
        return false;
    }

    /**
     * Cards on FlashRecovery and FlashKernel Dialog
     */
    public void FlashSupportedRecovery(Card card) {
        final File path;
        final ArrayList<String> Versions;
        if (!mDevice.downloadUtils(mContext)) {
            /**
             * If there files be needed to flash download it and listing device specified
             * recovery file for example recovery-clockwork-touch-6.0.3.1-grouper.img
             * (read out from RECOVERY_SUMS)
             */
            String SYSTEM = card.getData().toString();
            ArrayAdapter<String> VersionsAdapter = new ArrayAdapter<>(mContext,
                    R.layout.custom_list_item);
            switch (SYSTEM) {
                case "stock":
                    Versions = mDevice.getStockRecoveryVersions();
                    path = Constants.PathToStockRecovery;
                    for (String i : Versions) {
                        try {
                            String version = i.split("-")[3].replace(mDevice.getRecoveryExt(), "");
                            String deviceName = i.split("-")[2];
                            VersionsAdapter.add("Stock Recovery " + version + " (" + deviceName + ")");
                        } catch (ArrayIndexOutOfBoundsException e) {
                            VersionsAdapter.add(i);
                        }
                    }
                    break;
                case "clockwork":
                    Versions = mDevice.getCwmRecoveryVersions();
                    path = Constants.PathToCWM;
                    for (String i : Versions) {
                        try {
                            int startIndex;
                            String version = "";
                            if (i.contains("-touch-")) {
                                startIndex = 4;
                                version = "Touch ";
                            } else {
                                startIndex = 3;
                            }
                            version += i.split("-")[startIndex-1];
                            String device = "(";
                            for (int splitNr = startIndex; splitNr < i.split("-").length; splitNr++) {
                                if (!device.equals("(")) device += "-";
                                device += i.split("-")[splitNr].replace(mDevice.getRecoveryExt(), "");
                            }
                            device += ")";
                            VersionsAdapter.add("ClockworkMod " + version + " " + device);
                        } catch (ArrayIndexOutOfBoundsException e) {
                            VersionsAdapter.add(i);
                        }
                    }
                    break;
                case "twrp":
                    Versions = mDevice.getTwrpRecoveryVersions();
                    path = Constants.PathToTWRP;
                    for (String i : Versions) {
                        try {
                            if (i.contains("openrecovery")) {
                                String device = "(";
                                for (int splitNr = 3; splitNr < i.split("-").length; splitNr++) {
                                    if (!device.equals("(")) device += "-";
                                    device += i.split("-")[splitNr].replace(mDevice.getRecoveryExt(), "");
                                }
                                device += ")";
                                VersionsAdapter.add("TWRP " + i.split("-")[2] + " " + device);
                            } else {
                                VersionsAdapter.add("TWRP " + i.split("-")[1].replace(mDevice.getRecoveryExt(), "") + ")");
                            }
                        } catch (ArrayIndexOutOfBoundsException e) {
                            VersionsAdapter.add(i);
                        }
                    }
                    break;
                case "philz":
                    Versions = mDevice.getPhilzRecoveryVersions();
                    path = Constants.PathToPhilz;
                    for (String i : Versions) {
                        try {
                            String device = "(";
                            for (int splitNr = 1; splitNr < i.split("-").length; splitNr++) {
                                if (!device.equals("(")) device += "-";
                                device += i.split("-")[splitNr].replace(mDevice.getRecoveryExt(), "");
                            }
                            device += ")";
                            VersionsAdapter.add("PhilZ Touch " + i.split("_")[2].split("-")[0] + " " + device);
                        } catch (ArrayIndexOutOfBoundsException e) {
                            VersionsAdapter.add(i);
                        }
                    }
                    break;
                default:
                    return;
            }
            final AppCompatDialog RecoveriesDialog = new AppCompatDialog(mContext);
            RecoveriesDialog.setTitle(SYSTEM.toUpperCase());
            ListView VersionList = new ListView(mContext);
            RecoveriesDialog.setContentView(VersionList);

            VersionList.setAdapter(VersionsAdapter);
            RecoveriesDialog.show();
            VersionList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                    RecoveriesDialog.dismiss();

                    final String fileName = Versions.get(i);
                    final File recovery = new File(path, fileName);
                    if (!recovery.exists()) {
                        try {
                            URL url = new URL(Constants.RECOVERY_URL + "/" + fileName);
                            Downloader RecoveryDownloader = new Downloader(mContext, url, recovery);
                            RecoveryDownloader.setOnDownloadListener(new Downloader.OnDownloadListener() {
                                @Override
                                public void success(File file) {
                                    flashRecovery(file);
                                }

                                @Override
                                public void failed(Exception e) {

                                }
                            });
                            RecoveryDownloader.setRetry(true);
                            RecoveryDownloader.setAskBeforeDownload(true);
                            RecoveryDownloader.setChecksumFile(RecoveryCollectionFile);
                            RecoveryDownloader.ask();
                        } catch (MalformedURLException ignored) {}
                    } else {
                        flashRecovery(recovery);
                    }
                }
            });
        }
    }

    /**
     * Flash Recovery from storage (already downloaded)
     */
    public void bFlashOtherRecovery(View view) {
        String AllowedEXT[] = {mDevice.getRecoveryExt()};
        FileChooserDialog chooser = new FileChooserDialog(view.getContext());
        chooser.setAllowedEXT(AllowedEXT);
        chooser.setBrowseUpAllowed(true);
        chooser.setOnFileChooseListener(new FileChooserDialog.OnFileChooseListener() {
                @Override
                public void OnFileChoose(File file) {
                    flashRecovery(file);
                }
            });
        chooser.setStartFolder(Constants.PathToSd);
        chooser.setWarn(true);
        chooser.show();
    }

    public void FlashSupportedKernel(Card card) {
        final File path;
        ArrayList<String> Versions;
        ArrayAdapter<String> VersionsAdapter = new ArrayAdapter<>(mContext, R.layout.custom_list_item);
        if (!mDevice.downloadUtils(mContext)) {
            /**
             * If there files be needed to flash download it and listing device specified recovery
             * file for example stock-boot-grouper-4.4.img (read out from kernel_sums)
             */
            String SYSTEM = card.getData().toString();
            if (SYSTEM.equals("stock")) {
                Versions = mDevice.getStockKernelVersions();
                path = Constants.PathToStockKernel;
                for (String i : Versions) {
                    try {
                        String version = i.split("-")[3].replace(mDevice.getRecoveryExt(), "");
                        String deviceName = i.split("-")[2];
                        VersionsAdapter.add("Stock Kernel " + version + " (" + deviceName + ")");
                    } catch (ArrayIndexOutOfBoundsException e) {
                        VersionsAdapter.add(i);
                    }
                }
            } else {
                return;
            }

            final AppCompatDialog KernelDialog = new AppCompatDialog(mContext);
            KernelDialog.setTitle(SYSTEM);
            ListView VersionList = new ListView(mContext);
            KernelDialog.setContentView(VersionList);
            VersionList.setAdapter(VersionsAdapter);
            KernelDialog.show();
            VersionList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                    KernelDialog.dismiss();
                    final String fileName;
                    if ((fileName = ((AppCompatTextView) view).getText().toString()) != null) {
                        final File kernel = new File(path, fileName);

                        if (!kernel.exists()) {
                            try {
                                URL url = new URL(Constants.KERNEL_URL + "/" + fileName);
                                Downloader KernelDownloader = new Downloader(mContext, url, kernel);
                                KernelDownloader.setOnDownloadListener(new Downloader.OnDownloadListener() {
                                    @Override
                                    public void success(File file) {
                                        flashKernel(file);
                                    }

                                    @Override
                                    public void failed(Exception e) {

                                    }
                                });
                                KernelDownloader.setRetry(true);
                                KernelDownloader.setAskBeforeDownload(true);
                                KernelDownloader.setChecksumFile(KernelCollectionFile);
                                KernelDownloader.ask();
                            } catch (MalformedURLException ignored) {}
                        } else {
                            flashKernel(kernel);
                        }
                    }
                }
            });
        }
    }

    /**
     * Flash Kernel from storage (separate downloaded)
     */
    public void bFlashOtherKernel(View view) {
        FileChooserDialog chooser = new FileChooserDialog(view.getContext());
        String AllowedEXT[] = {mDevice.getKernelExt()};
        chooser.setOnFileChooseListener(new FileChooserDialog.OnFileChooseListener() {
            @Override
            public void OnFileChoose(File file) {
                flashKernel(file);
            }
        });
        chooser.setStartFolder(Constants.PathToSd);
        chooser.setAllowedEXT(AllowedEXT);
        chooser.setBrowseUpAllowed(true);
        chooser.setWarn(true);
        chooser.show();
    }

    public void showFlashHistory() {
        final ArrayList<File> HistoryFiles = new ArrayList<>();
        final ArrayList<String> HistoryFileNames = new ArrayList<>();
        final AppCompatDialog HistoryDialog = new AppCompatDialog(mContext);
        HistoryDialog.setTitle(R.string.history);
        ListView HistoryList = new ListView(mContext);
        File tmp;
        for (int i = 0; i < 5; i++) {
            tmp = new File(Common.getStringPref(mContext, Constants.PREF_NAME,
                    Constants.PREF_KEY_HISTORY + String.valueOf(i)));
            if (tmp.exists() && !tmp.isDirectory()) {
                HistoryFiles.add(tmp);
                HistoryFileNames.add(tmp.getName());
            } else {
                Common.setStringPref(mContext, Constants.PREF_NAME,
                        Constants.PREF_KEY_HISTORY + String.valueOf(i), "");
            }
        }
        HistoryList.setAdapter(new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1,
                HistoryFileNames));
        HistoryList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {

                if (HistoryFiles.get(arg2).exists()) {
                    mActivity.switchTo(FlashAsFragment.newInstance(mActivity,
                            HistoryFiles.get(arg2), true));
                    HistoryDialog.dismiss();
                }
            }
        });
        HistoryDialog.setContentView(HistoryList);
        if (HistoryFileNames.toArray().length > 0) {
            HistoryDialog.show();
        } else {
            Toast
                    .makeText(mActivity, R.string.no_history, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void flashRecovery(final File recovery) {
        if (recovery != null) {
            if (recovery.exists() && recovery.getName().endsWith(mDevice.getRecoveryExt())
                    && !recovery.isDirectory()) {
                if (!mDevice.isFOTAFlashed() && !mDevice.isRecoveryOverRecovery()) {
                    /** Flash not need to be handled specially */
                    executeFlash(recovery, FlashUtil.JOB_FLASH_RECOVERY);
                } else {
                    /** Flashing needs to be handled specially (not standard flash method)*/
                    if (mDevice.isFOTAFlashed()) {
                        /** Show warning if FOTAKernel will be flashed */
                        new AlertDialog.Builder(mContext)
                                .setTitle(R.string.warning)
                                .setMessage(R.string.fota)
                                .setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        executeFlash(recovery, FlashUtil.JOB_FLASH_RECOVERY);
                                    }
                                })
                                .setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                    }
                                })
                                .show();
                    } else {
                        mActivity.switchTo(ScriptManagerFragment.newInstance(mActivity, recovery));
                    }
                }
            }
        }
    }

    private void flashKernel(final File kernel) {
        if (kernel != null) {
            if (kernel.exists() && kernel.getName().endsWith(mDevice.getKernelExt())
                    && !kernel.isDirectory()) {
                executeFlash(kernel, FlashUtil.JOB_FLASH_KERNEL);
            }
        }
    }

    private void executeFlash(final File image, final int FlashUtilJOB) {
        final FlashUtil flashUtil = new FlashUtil(mActivity, image, FlashUtilJOB);
        flashUtil.execute();
    }

    public void optimizeLayout(View root) throws NullPointerException {

        if (mDevice.isRecoverySupported() || mDevice.isKernelSupported()) {
            /** If device is supported start setting up layout */
            setupSwipeUpdater(root);

            CardUI RashrCards = (CardUI) root.findViewById(R.id.RashrCards);
            final CardColorScheme scheme;
            Resources res = getResources();
            if (!RashrActivity.isDark) {
                scheme = null;
            } else {
                scheme = new CardColorScheme(
                        res.getColor(R.color.background_floating_material_dark),
                        res.getColor(R.color.abc_secondary_text_material_dark)
                );
            }
            /** Avoid overlapping scroll on CardUI and SwipeRefreshLayout */
            RashrCards.getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {

                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    int topRowVerticalPosition = (view == null || view.getChildCount() == 0) ?
                            0 : view.getChildAt(0).getTop();
                    mSwipeUpdater.setEnabled((topRowVerticalPosition >= 0));
                }
            });

            if (mDevice.isRecoverySupported()) {
                addRecoveryCards(RashrCards, scheme);
            }

            if (mDevice.isKernelSupported()) {
                addKernelCards(RashrCards, scheme);
            }

            final IconCard HistoryCard = new IconCard(getString(R.string.history),
                    R.drawable.ic_history, getString(R.string.history_description), scheme);
            HistoryCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFlashHistory();
                }
            });

            RashrCards.addCard(HistoryCard, true);

            addRebooterCards(RashrCards, scheme);
        }
    }

    public void showUnifiedBuildsDialog() {

        final AppCompatDialog UnifiedBuildsDialog = new AppCompatDialog(mContext);
        UnifiedBuildsDialog.setTitle(R.string.make_choice);
        final ArrayList<String> DevName = new ArrayList<>();
        ArrayList<String> DevNamesCarriers = new ArrayList<>();

        UnifiedBuildsDialog.setContentView(R.layout.dialog_unified_build);
        ListView UnifiedList = (ListView) UnifiedBuildsDialog.findViewById(R.id.lvUnifiedList);
        ArrayAdapter<String> UnifiedAdapter = new ArrayAdapter<>(mContext,
                android.R.layout.simple_list_item_1, DevNamesCarriers);
        UnifiedList.setAdapter(UnifiedAdapter);

        if (mDevice.getManufacture().equals("samsung")) {
            String[] unifiedGalaxyS3 = {"d2lte", "d2att", "d2cri", "d2mtr",
                    "d2spr", "d2tmo", "d2usc", "d2vzw"};
            String[] unifiedGalaxyNote3 = {"hlte", "hltespr", "hltetmo", "hltevzw", "htlexx"};
            String[] unifiedGalaxyS4 = {"jflte", "jflteatt", "jfltecan", "jfltecri", "jfltecsp",
                    "jfltespr", "jfltetmo", "jflteusc", "jfltevzw", "jfltexx", "jgedlte"};
            if (Common.stringEndsWithArray(mDevice.getName(), unifiedGalaxyS3)) {
                DevName.addAll(Arrays.asList(unifiedGalaxyS3));
            } else if (Common.stringEndsWithArray(mDevice.getName(), unifiedGalaxyS3)) {
                DevName.addAll(Arrays.asList(unifiedGalaxyNote3));
            } else if (Common.stringEndsWithArray(mDevice.getName(), unifiedGalaxyS4)) {
                DevName.addAll(Arrays.asList(unifiedGalaxyS4));
            }
        }

        if (mDevice.getManufacture().equals("motorola")) {
            String[] unifiedMsm8960 = {"moto_msm8960"};
            if (mDevice.getBOARD().equals("msm8960")) {
                DevName.addAll(Arrays.asList(unifiedMsm8960));
            }
        }

        for (String i : DevName) {
            if (i.contains("att")) {
                DevNamesCarriers.add(i + " (AT&T Mobility)");
            } else if (i.contains("can")) {
                DevNamesCarriers.add(i + " (Canada)");
            } else if (i.contains("cri")) {
                DevNamesCarriers.add(i + " (Cricket Wireless)");
            } else if (i.contains("csp")) {
                DevNamesCarriers.add(i + " (C Spire Wireless)");
            } else if (i.contains("mtr")) {
                DevNamesCarriers.add(i + " (MetroPCS)");
            } else if (i.contains("spr")) {
                DevNamesCarriers.add(i + " (Sprint Corporation)");
            } else if (i.contains("tmo")) {
                DevNamesCarriers.add(i + " (T-Mobile US)");
            } else if (i.contains("usc")) {
                DevNamesCarriers.add(i + " (U.S. Cellular)");
            } else if (i.contains("vzw")) {
                DevNamesCarriers.add(i + " (Verizon Wireless)");
            } else if (i.contains("xx")) {
                DevNamesCarriers.add(i + " (International)");
            } else if (i.contains("ged")) {
                DevNamesCarriers.add(i + " (Google Play Edition)");
            } else {
                DevNamesCarriers.add(i + " (Unified)");
            }
        }
        UnifiedList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                UnifiedBuildsDialog.dismiss();
                final ProgressDialog reloading = new ProgressDialog(mContext);
                reloading.setMessage(mContext.getString(R.string.reloading));
                reloading.setCancelable(false);
                reloading.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Common.setBooleanPref(mContext, Constants.PREF_NAME,
                                Constants.PREF_KEY_SHOW_UNIFIED, false);
                        mDevice.setName(DevName.get(position));
                        mDevice.loadRecoveryList();
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloading.dismiss();
                                mActivity.switchTo(FlashFragment.newInstance(mActivity));
                            }
                        });
                    }
                }).start();

            }
        });
        AppCompatButton KeepCurrent = (AppCompatButton) UnifiedBuildsDialog.findViewById(R.id.bKeepCurrent);
        KeepCurrent.setText(String.format(getString(R.string.keep_current_name), mDevice.getName()));
        KeepCurrent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Common.setBooleanPref(mContext, Constants.PREF_NAME, Constants.PREF_KEY_SHOW_UNIFIED, false);
                UnifiedBuildsDialog.dismiss();
            }
        });

        if (DevName.size() > 0) {
            UnifiedBuildsDialog.show();
            UnifiedBuildsDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Common.setBooleanPref(mContext, Constants.PREF_NAME, Constants.PREF_KEY_SHOW_UNIFIED,
                            false);
                }
            });
        }
    }
    public void setupSwipeUpdater(View root) {
        mSwipeUpdater = (SwipeRefreshLayout) root.findViewById(R.id.swipe_updater);
        mSwipeUpdater.setColorSchemeResources(R.color.custom_green,
                R.color.golden,
                R.color.custom_green,
                android.R.color.darker_gray);
        mSwipeUpdater.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                catchUpdates(false);
            }
        });

    }

    public void addRecoveryCards(CardUI cardUI, CardColorScheme scheme) {
        if (mDevice.isCwmRecoverySupported()) {
            final IconCard CWMCard = new IconCard(getString(R.string.sCWM), R.drawable.ic_cwm,
                    getString(R.string.cwm_description), scheme);
            CWMCard.setData("clockwork");
            CWMCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FlashSupportedRecovery(CWMCard);
                }
            });
            cardUI.addCard(CWMCard, true);
        }
        if (mDevice.isTwrpRecoverySupported()) {
            final IconCard TWRPCard = new IconCard(getString(R.string.sTWRP), R.drawable.ic_twrp,
                    getString(R.string.twrp_description), scheme);
            TWRPCard.setData("twrp");
            TWRPCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FlashSupportedRecovery(TWRPCard);
                }
            });
            cardUI.addCard(TWRPCard, true);
        }
        if (mDevice.isPhilzRecoverySupported()) {
            final SimpleCard PHILZCard = new SimpleCard(getString(R.string.sPhilz),
                    getString(R.string.philz_description), scheme);
            PHILZCard.setData("philz");
            PHILZCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FlashSupportedRecovery(PHILZCard);
                }
            });
            cardUI.addCard(PHILZCard, true);
        }
        if (mDevice.isStockRecoverySupported()) {
            final IconCard StockCard = new IconCard(getString(R.string.stock_recovery),
                    R.drawable.ic_update, getString(R.string.stock_recovery_description), scheme);
            StockCard.setData("stock");
            StockCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FlashSupportedRecovery(StockCard);
                }
            });
            cardUI.addCard(StockCard, true);
        }

        final SimpleCard OtherCard = new SimpleCard(getString(R.string.other_recovery),
                getString(R.string.other_storage_description), scheme);
        OtherCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bFlashOtherRecovery(v);
            }
        });
        cardUI.addCard(OtherCard, true);
    }
    public void addKernelCards(CardUI cardUI, CardColorScheme scheme) {
        if (mDevice.isStockKernelSupported()) {
            final IconCard StockCard = new IconCard(getString(R.string.stock_kernel), R.drawable.ic_stock,
                    getString(R.string.stock_kernel_description), scheme);
            StockCard.setData("stock");
            StockCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FlashSupportedKernel(StockCard);
                }
            });
            cardUI.addCard(StockCard, true);
        }

        final SimpleCard OtherCard = new SimpleCard(getString(R.string.other_kernel),
                getString(R.string.other_storage_description), scheme);
        OtherCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bFlashOtherKernel(v);
            }
        });

        cardUI.addCard(OtherCard, true);
    }
    public void addRebooterCards(CardUI cardUI, CardColorScheme scheme) {
        SimpleCard Reboot = new SimpleCard(getString(R.string.sReboot),
                getString(R.string.reboot_description), scheme);
        Reboot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final AlertDialog.Builder ConfirmationDialog = new AlertDialog.Builder(mContext);
                ConfirmationDialog.setTitle(R.string.warning);
                ConfirmationDialog.setMessage(R.string.reboot_confirmation);
                ConfirmationDialog.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mToolbox.reboot(Toolbox.REBOOT_REBOOT);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                ConfirmationDialog.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                ConfirmationDialog.show();
            }
        });
        SimpleCard RebootRecovery = new SimpleCard(getString(R.string.sRebootRecovery),
                getString(R.string.reboot_recovery_description), scheme);
        RebootRecovery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder ConfirmationDialog = new AlertDialog.Builder(mContext);
                ConfirmationDialog.setTitle(R.string.warning);
                ConfirmationDialog.setMessage(R.string.reboot_confirmation);
                ConfirmationDialog.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mToolbox.reboot(Toolbox.REBOOT_RECOVERY);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                ConfirmationDialog.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                ConfirmationDialog.show();
            }
        });
        SimpleCard RebootBootloader = new SimpleCard(getString(R.string.sRebootBootloader),
                getString(R.string.reboot_bootloader_description), scheme);
        RebootBootloader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder ConfirmationDialog = new AlertDialog.Builder(mContext);
                ConfirmationDialog.setTitle(R.string.warning);
                ConfirmationDialog.setMessage(R.string.reboot_confirmation);
                ConfirmationDialog.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mToolbox.reboot(Toolbox.REBOOT_BOOTLOADER);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                ConfirmationDialog.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                ConfirmationDialog.show();
            }
        });
        SimpleCard Shutdown = new SimpleCard(getString(R.string.sRebootShutdown),
                getString(R.string.shutdown_description), scheme);
        Shutdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder ConfirmationDialog = new AlertDialog.Builder(mContext);
                ConfirmationDialog.setTitle(R.string.warning);
                ConfirmationDialog.setMessage(R.string.shutdown_confirmation);
                ConfirmationDialog.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mToolbox.reboot(Toolbox.REBOOT_SHUTDOWN);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                ConfirmationDialog.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                ConfirmationDialog.show();
            }
        });
        cardUI.addCard(Reboot, true);
        cardUI.addCard(RebootRecovery, true);
        cardUI.addCard(RebootBootloader, true);
        cardUI.addCard(Shutdown, true);
    }

    public void setDevice(Device device) {
        mDevice = device;
    }
    public void setToolbox(Toolbox toolbox) {
        mToolbox = toolbox;
    }
    public void setActivity(RashrActivity activity) {
        mActivity = activity;
        mContext = activity;
    }

    public void catchUpdates(final boolean ask) {
        mSwipeUpdater.setRefreshing(true);
        final Thread updateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    /** Check changes on server */
                    final URL recoveryUrl = new URL(Constants.RECOVERY_SUMS_URL);
                    URLConnection recoveryCon = recoveryUrl.openConnection();
                    long recoveryListSize = recoveryCon.getContentLength();         //returns size of file on server
                    long recoveryListLocalSize = RecoveryCollectionFile.length();   //returns size of local file
                    if (recoveryListSize > 0) {
                        isRecoveryListUpToDate = recoveryListLocalSize == recoveryListSize;
                    }
                    final URL kernelUrl = new URL(Constants.KERNEL_SUMS_URL);
                    URLConnection kernelCon = kernelUrl.openConnection();
                    long kernelListSize = kernelCon.getContentLength();
                    long kernelListLocalSize = KernelCollectionFile.length();
                    if (kernelListSize > 0) {
                        isKernelListUpToDate = kernelListLocalSize == kernelListSize;
                    }
                } catch (IOException e) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast
                                    .makeText(mContext, R.string.check_connection, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
                    mActivity.addError(Constants.RASHR_TAG, e, false);
                }
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isRecoveryListUpToDate || !isKernelListUpToDate) {
                            /** Counting current images */
                            final int img_count = mDevice.getStockRecoveryVersions().size()
                                    + mDevice.getCwmRecoveryVersions().size()
                                    + mDevice.getTwrpRecoveryVersions().size()
                                    + mDevice.getPhilzRecoveryVersions().size()
                                    + mDevice.getStockKernelVersions().size();
                            final URL recoveryURL;
                            final URL kernelURL;
                            try {
                                recoveryURL = new URL(Constants.RECOVERY_SUMS_URL);
                                kernelURL = new URL(Constants.KERNEL_SUMS_URL);
                            } catch (MalformedURLException e) {
                                return;
                            }
                            final Downloader RecoveryUpdater = new Downloader(mContext, recoveryURL,
                                    RecoveryCollectionFile);
                            RecoveryUpdater.setOverrideFile(true);
                            RecoveryUpdater.setOnDownloadListener(new Downloader.OnDownloadListener() {
                                @Override
                                public void success(File file) {
                                    mDevice.loadRecoveryList();
                                    isRecoveryListUpToDate = true;
                                    final Downloader KernelUpdater = new Downloader(mContext, kernelURL,
                                            KernelCollectionFile);
                                    KernelUpdater.setOverrideFile(true);
                                    KernelUpdater.setOnDownloadListener(new Downloader.OnDownloadListener() {
                                        @Override
                                        public void success(File file) {
                                            mDevice.loadKernelList();
                                            isKernelListUpToDate = true;
                                            /** Counting added images (after update) */
                                            final int new_img_count = (mDevice.getStockRecoveryVersions().size()
                                                    + mDevice.getCwmRecoveryVersions().size()
                                                    + mDevice.getTwrpRecoveryVersions().size()
                                                    + mDevice.getPhilzRecoveryVersions().size()
                                                    + mDevice.getStockKernelVersions().size()) - img_count;
                                            mActivity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (isAdded()) {
                                                        Toast
                                                                .makeText(mActivity, String.format(getString(R.string.new_imgs_loaded),
                                                                        new_img_count), Toast.LENGTH_SHORT)
                                                                .show();
                                                    }
                                                    mSwipeUpdater.setRefreshing(false);
                                                }
                                            });
                                        }

                                        @Override
                                        public void failed(final Exception e) {
                                            Toast
                                                    .makeText(mActivity, e.getMessage(), Toast.LENGTH_SHORT)
                                                    .show();
                                            mSwipeUpdater.setRefreshing(false);
                                        }
                                    });
                                    KernelUpdater.execute();
                                }

                                @Override
                                public void failed(final Exception e) {
                                    Toast
                                            .makeText(mActivity, e.getMessage(), Toast.LENGTH_SHORT)
                                            .show();
                                    mSwipeUpdater.setRefreshing(false);
                                }
                            });
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (ask) {
                                        AlertDialog.Builder updateDialog = new AlertDialog.Builder(mContext);
                                        updateDialog
                                                .setTitle(R.string.update_available)
                                                .setMessage(R.string.lists_outdated)
                                                .setPositiveButton(R.string.update, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        Toast
                                                                .makeText(mActivity, R.string.refresh_list, Toast.LENGTH_SHORT)
                                                                .show();
                                                        RecoveryUpdater.execute();
                                                    }
                                                })
                                                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {

                                                    }
                                                })
                                                .show();
                                    } else {
                                        Toast
                                                .makeText(mActivity, R.string.refresh_list, Toast.LENGTH_SHORT)
                                                .show();
                                        RecoveryUpdater.execute();
                                    }
                                }
                            });
                        } else {
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast
                                            .makeText(mContext, R.string.uptodate, Toast.LENGTH_SHORT)
                                            .show();
                                    mSwipeUpdater.setRefreshing(false);
                                }
                            });
                        }
                    }
                });
            }
        });
        updateThread.start();
    }
}

