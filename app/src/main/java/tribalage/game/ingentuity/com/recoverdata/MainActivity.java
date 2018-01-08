package tribalage.game.ingentuity.com.recoverdata;

import android.app.ProgressDialog;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.bigkoo.alertview.AlertView;
import com.bigkoo.alertview.OnItemClickListener;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;

public class MainActivity extends AppCompatActivity {

    /**
     * 应用的包名
     */
    private static final String AppPackageName = "com.tap4fun.brutalage_test";

    /**
     * 应用的启动类
     */
    private static final String AppLauncherActivity = "com.tap4fun.project.CustomGameActivity";

    /**
     * 恢复的目标应用 data 存储绝对路径
     */
    private static final String BasePath = "/data/data/com.tap4fun.brutalage_test";

    /**
     * 存储备份的文件夹名字
     */
    private static final String SaveBackupDirName = "Pictures/野蛮时代";

    private ListView lvFiles;
    private ArrayAdapter<String> arrayAdapter;

    private long pressedTime = 0;

    private String curSelectName = null;

    private ProgressDialog dialogRestoreBackup;
    private ProgressDialog dialogBackupCurrent;

    private AsyncTask<Void, Void, Boolean> asyncRestoreBackupTask;
    private AsyncTask<Void, Void, Boolean> asyncBackupCurrentTask;

    private AlertView alertView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lvFiles = (ListView) findViewById(R.id.lvFiles);
        lvFiles.setAdapter(arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1));
        lvFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                curSelectName = arrayAdapter.getItem(position);
            }
        });

        dialogRestoreBackup = new ProgressDialog(this);
        dialogRestoreBackup.setTitle("操作提示");
        dialogRestoreBackup.setMessage("备份数据恢复中，请稍后...");

        dialogBackupCurrent = new ProgressDialog(this);
        dialogBackupCurrent.setTitle("操作提示");
        dialogBackupCurrent.setMessage("当前数据备份中，请稍后...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (asyncRestoreBackupTask != null) {
            asyncRestoreBackupTask.cancel(false);
            asyncRestoreBackupTask = null;
        }
        if (asyncBackupCurrentTask != null) {
            asyncBackupCurrentTask.cancel(false);
            asyncBackupCurrentTask = null;
        }
        if (dialogRestoreBackup != null && dialogRestoreBackup.isShowing()) {
            dialogRestoreBackup.dismiss();
            dialogRestoreBackup = null;
        }
        if (dialogBackupCurrent != null && dialogBackupCurrent.isShowing()) {
            dialogBackupCurrent.dismiss();
            dialogBackupCurrent = null;
        }
        if (alertView != null && alertView.isShowing()) {
            alertView.dismiss();
            alertView = null;
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        updateAdapterData();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0 && alertView != null && alertView.isShowing()) {
                alertView.dismiss();
                ;
                alertView = null;
                // 终止传播
                return true;
            }

            // 获取第一次按键时间
            long mNowTime = System.currentTimeMillis();
            if ((mNowTime - pressedTime) > 2000) {
                // 比较两次按键时间差
                Toasty.info(this, "再按一次退出程序").show();
                pressedTime = mNowTime;
            } else {
                // 退出程序
                finish();
            }

            // 终止传播
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 点击刷新
     */
    public void onClickRefresh(View view) {
        updateAdapterData();
    }

    /**
     * 点击恢复
     */
    public void onClickRecover(View view) {
        if (curSelectName == null) {
            Toasty.warning(MainActivity.this, "请先选择需要恢复项").show();
            return;
        }
        if (!isInstalled(AppPackageName)) {
            Toasty.warning(MainActivity.this, "请先安装需要恢复数据的应用").show();
            return;
        }

        if (asyncRestoreBackupTask != null) {
            asyncRestoreBackupTask.cancel(false);
            asyncRestoreBackupTask = null;
        }
        asyncRestoreBackupTask = new RestoreBackupTask().execute();
    }

    /**
     * 更新适配器数据
     */
    private void updateAdapterData() {
        // 更新 list view 的数据适配器
        arrayAdapter.clear();
        arrayAdapter.addAll(obtainBackupNames());
        arrayAdapter.notifyDataSetChanged();
    }

    /**
     * 获取备份名列表
     */
    private ImmutableList<String> obtainBackupNames() {
        ArrayList<String> result = Lists.newArrayList();

        // 获取存储备份路径下的备份列表
        File saveBackupDirFile = getBackupFile();
        // 目标存在且是目录
        if (saveBackupDirFile.exists() && saveBackupDirFile.isDirectory()) {
            // 获取该目录下的所有文件和目录
            for (File childFile : saveBackupDirFile.listFiles()) {
                // 是文件夹
                if (childFile.isDirectory()) {
                    result.add(childFile.getName());
                }
            }
        }

        return ImmutableList.copyOf(result);
    }

    /**
     * 获取存储备份的文件夹
     */
    private File getBackupFile() {
        // 外部存储路径
        File sdDirFile = Environment.getExternalStorageDirectory().getAbsoluteFile();
        // 存储备份路径
        return new File(sdDirFile, SaveBackupDirName);
    }

    /**
     * 检查系统中是否安装了某个应用
     */
    private boolean isInstalled(final String packageName) {
        if (Strings.isNullOrEmpty(packageName)) {
            return false;
        }
        // 获取所有已安装程序的包信息
        for (PackageInfo temp : getPackageManager().getInstalledPackages(0)) {
            if (temp != null && temp.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 备份当前的
     */
    public void onClickBackupCurrent(View view) {
        if (!isInstalled(AppPackageName)) {
            Toasty.warning(MainActivity.this, "未安装需要备份数据的应用").show();
            return;
        }
        if (!new File(BasePath).exists()) {
            Toasty.warning(MainActivity.this, "无需备份,应用未产生数据").show();
            return;
        }
        if (asyncBackupCurrentTask != null) {
            asyncBackupCurrentTask.cancel(false);
            asyncBackupCurrentTask = null;
        }

        if (alertView != null && alertView.isShowing()) {
            alertView.dismiss();
            alertView = null;
        }

        ImmutableList<String> temps = obtainBackupNames();
        String[] items = null;
        if (temps.isEmpty()) {
            items = new String[]{"default"};
        } else {
            items = temps.toArray(new String[temps.size()]);
        }
        final String[] finalItems = items;
        alertView = new AlertView("应用数据备份到...", null, null, null, items, this, AlertView.Style.ActionSheet, new OnItemClickListener() {
            @Override
            public void onItemClick(Object o, int position) {
                asyncBackupCurrentTask = new BackupCurrentTask(finalItems[position]).execute();
            }
        }).setCancelable(true);
        alertView.show();
    }

    private class BackupCurrentTask extends AsyncTask<Void, Void, Boolean> {

        private String backupName;

        public BackupCurrentTask(String backupName) {
            this.backupName = backupName;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialogBackupCurrent.show();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            dialogBackupCurrent.dismiss();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                // 指令集
                List<String> commands = Lists.newArrayList();

                File targetSavePath = new File(getBackupFile(), backupName);
                if (targetSavePath.exists()) {
                    // 安全删除，避免 Device or resource busy
                    String temp = targetSavePath.getAbsolutePath() + "-" + System.currentTimeMillis();
                    // 先重命名，在删除
                    commands.add(String.format("mv %s %s", targetSavePath.getAbsolutePath(), temp));
                    commands.add(String.format("rm -r %s", temp));
                }

                // 杀死程序
                commands.add(String.format("am force-stop %s", AppPackageName));

                // 复制当前应用数据到备份目录
                commands.add(String.format("cp -rf %s/ %s/", BasePath, targetSavePath.getAbsolutePath()));

                // 执行
                ShellUtils.CommandResult commandResult = ShellUtils.execCommand(commands, true);

                Log.d("cmd集", commands.toString());
                Log.d("备份", commandResult.toString());
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            dialogBackupCurrent.dismiss();

            if (result) {
                Toasty.success(MainActivity.this, "备份成功", Toast.LENGTH_LONG).show();
                updateAdapterData();
            } else {
                Toasty.error(MainActivity.this, "备份失败", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private class RestoreBackupTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialogRestoreBackup.show();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            dialogRestoreBackup.dismiss();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                // 指令集
                List<String> commands = Lists.newArrayList();

                // 清理指定应用的 data/data 数据
                if (new File(BasePath).exists()) {
                    commands.add(String.format("rm -r %s", BasePath));
                }
                // 复制
                commands.add(String.format("cp -rf %s/ %s/", new File(getBackupFile(), curSelectName).getAbsolutePath(), BasePath));
                // 修改权限
                commands.add(String.format("chmod -R 777 %s", BasePath));
                // 杀死程序
                commands.add(String.format("am force-stop %s", AppPackageName));
                // 启动程序
                commands.add(String.format("am start -n %s/%s", AppPackageName, AppLauncherActivity));

                // 执行
                ShellUtils.CommandResult commandResult = ShellUtils.execCommand(commands, true);

                Log.d("cmd集", commands.toString());
                Log.d("恢复", commandResult.toString());
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            dialogRestoreBackup.dismiss();

            if (result) {
                Toasty.success(MainActivity.this, String.format("恢复成功:%s", curSelectName), Toast.LENGTH_LONG).show();
            } else {
                Toasty.error(MainActivity.this, String.format("恢复失败:%s", curSelectName), Toast.LENGTH_SHORT).show();
            }
        }
    }

}
