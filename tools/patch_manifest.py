import io

p = r"C:\Users\Administrator\.openclaw\workspace\projects\casio-calc-android\app\src\main\AndroidManifest.xml"
s = io.open(p, encoding="utf-8").read()

if 'android:launchMode="singleTop"' not in s:
    old = '''        <activity
            android:name=".MainActivity"
            android:exported="true"'''
    new = '''        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"'''
    assert old in s, "MainActivity anchor missing"
    s = s.replace(old, new, 1)

if "ProcessTextActivity" not in s:
    anchor = "        <!-- 拍照解题：把相机拍的照片写进缓存目录再读回（不申请 CAMERA 权限） -->"
    block = '''        <!-- 选中文字 → 计算：系统文本选择菜单里的入口。
             自己无界面，把 text/plain 转交 MainActivity 后立刻退出。 -->
        <activity
            android:name=".ProcessTextActivity"
            android:exported="true"
            android:noHistory="true"
            android:excludeFromRecents="true"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.intent.action.PROCESS_TEXT" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>

        <!-- 通知栏快捷磁贴：一键打开计算器 -->
        <service
            android:name=".CalcTileService"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>

''' + anchor
    assert anchor in s, "provider anchor missing"
    s = s.replace(anchor, block, 1)

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("manifest patched")
