import os
import re
import shutil
import sys

ROOT = os.getcwd()
APP = os.path.join(ROOT, "android", "app", "src", "main")
PKG = "com.moi.launcherios"
JAVA_DIR = os.path.join(APP, "java", *PKG.split("."))


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)


# 1) Code natif (plugin + activité principale)
os.makedirs(JAVA_DIR, exist_ok=True)
for name in ("LauncherPlugin.java", "MainActivity.java"):
    shutil.copy(os.path.join(ROOT, "native", name), os.path.join(JAVA_DIR, name))
print("Java copié dans", JAVA_DIR)

# 2) Manifeste : launcher (HOME) + permissions
mp = os.path.join(APP, "AndroidManifest.xml")
m = read(mp)

if "android.intent.category.HOME" not in m:
    m, n = re.subn(
        r'(<category\s+android:name="android.intent.category.LAUNCHER"\s*/>)',
        r'\1\n                <category android:name="android.intent.category.HOME" />\n                <category android:name="android.intent.category.DEFAULT" />',
        m,
        count=1,
    )
    if n != 1:
        sys.exit("ERREUR: filtre LAUNCHER introuvable dans AndroidManifest.xml")

attrs = []
if "android:screenOrientation" not in m:
    attrs.append('android:screenOrientation="portrait"')
if "android:stateNotNeeded" not in m:
    attrs.append('android:stateNotNeeded="true"')
if "android:windowSoftInputMode" not in m:
    attrs.append('android:windowSoftInputMode="adjustResize"')
if attrs:
    m, n = re.subn(r"<activity\b", "<activity " + " ".join(attrs), m, count=1)
    if n != 1:
        sys.exit("ERREUR: balise <activity> introuvable")

for perm in ("android.permission.QUERY_ALL_PACKAGES", "android.permission.ACCESS_NETWORK_STATE"):
    if perm not in m:
        m = m.replace("<application", '<uses-permission android:name="%s" />\n    <application' % perm, 1)

write(mp, m)
print("Manifeste patché")

# 3) Fond noir au démarrage (pas de splash blanc)
sp = os.path.join(APP, "res", "values", "styles.xml")
if os.path.exists(sp):
    s = read(sp).replace("@drawable/splash", "@android:color/black")
    write(sp, s)
    print("styles.xml patché")

print("Patch terminé")
