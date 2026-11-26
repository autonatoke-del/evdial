"""Offline source invariants for EVDial visual profiles. No Android SDK required."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'src/main/java/ru/autonatoke/evdial'
checks=[]
def assert_contains(file,*parts):
    text=(JAVA/file).read_text()
    for p in parts:
        assert p in text, f'{file}: missing {p}'
        checks.append((file,p))
assert_contains('UiProfiles.java','"ev"','"light"','"pearl"','"minimal"','"akop"',
                'BUTTON =','PANEL =','KEYPAD =','NAV =','ACCENT =','CORNERS =','SCALE =',
                'skin_"+id(c)+"_"+key','static int opacity','static void reset')
assert_contains('ThemeStudio.java','ВЫБЕРИТЕ ОФОРМЛЕНИЕ','Тонкая настройка','private void sample(',
                'Прозрачность кнопок','Прозрачность цифровой клавиатуры','Прозрачность панелей',
                'Нижняя панель','Насыщенность акцентов','Скругление углов','Размер шрифта',
                'UiProfiles.select(this,preset.id)','UiProfiles.reset(this)')
assert_contains('Dialer.java','UiProfiles.keypad(this)','UiProfiles.signature(this)',
                'UiProfiles.navigation(activity)','UiProfiles.accent(this)','UiProfiles.value(this,UiProfiles.BUTTON)')
assert_contains('ElectricUi.java','UiProfiles.button(a)','buttonSurface(a,16)',
                'new Intent(a,EvBrowserActivity.class)')
assert_contains('EvBrowserActivity.java','EV browser')
assert_contains('PhoneThemes.java','KEY+"_"+profile')
assert_contains('WallpaperStore.java','custom_wallpaper_"+id','evdial-wallpaper-','wallpaper_revision_')
assert_contains('Appearance.java','UiProfiles.active(activity).base','contrast_"+UiProfiles.id(activity)')
assert_contains('DriverBadge.java','String title="EVDial"')
assert_contains('BatteryCallControl.java','UiProfiles.accent(getContext())')
assert not (ROOT/'src/main/res/drawable-nodpi/trirubina_mark.png').exists()
checks.append(('assets','removed brand mark is absent'))
for path in JAVA.glob('*.java'):
    content=path.read_text().casefold()
    assert all(word not in content for word in ('rubina','3rubina.ru','3 рубина')), path.name
checks.append(('production source','removed brand, profile and links are absent'))
assert "versionCode 21; versionName '0.3.3-rc7'" in (ROOT/'build.gradle').read_text()
checks.append(('build.gradle','0.3.3-rc7 release candidate version'))
print(f'PASS: {len(checks)} theme, linking, persistence and safety source assertions')
