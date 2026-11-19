# AfkAlan

WorldEdit/FAWE ile seçtiğin alanı **AFK alanı** yapar. Alanın içinde duran oyuncunun ekranının ortasında
geri sayım döner, süre dolunca oyuncuya sanal **AFK Parası** verilir ve sayaç baştan başlar.
Kazanılan para, `/afk market` ile açılan 45 slotluk markette harcanır.

**Desteklenen sürümler:** Spigot / Paper **1.16.5 → 1.21.x** (tek jar, Java 8+).
Her commit'te 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.1, 1.21.4 ve en güncel 1.21.x
Paper sunucularında WorldEdit ve PlaceholderAPI ile birlikte otomatik olarak test edilir (Actions sekmesi).

## Kurulum

1. [Releases](https://github.com/jesthub25/Afk-Alan/releases) sayfasından `AfkAlan-x.x.x.jar` indir, `plugins/` klasörüne at.
2. Alan oluşturmak için sunucuda **WorldEdit** veya **FastAsyncWorldEdit** olmalı.
3. İsteğe bağlı: **PlaceholderAPI** (placeholder'lar ve scoreboard satırlarında `%...%` desteği).

## Kullanım

1. `//wand` ile **pos1** ve **pos2** seç.
2. `/afk alan oluştur deneme` → alan `alanlar.yml` dosyasına kalıcı kaydedilir.
   (pos1/pos2 seçilmediyse uyarı verir.)
3. Oyuncu alana girince ekranın ortasında `60 → 1` geri sayım başlar, AFK scoreboard'u görünür.
4. Süre dolunca: AFK parası verilir, sadece oyuncunun göreceği mesaj gelir
   (`AFK ödülünüz verildi! Sanal Bakiyeniz: ...`), sayaç tekrar başlar.
5. Alandan çıkınca sayaç sıfırlanır, oyuncunun eski scoreboard'u geri gelir. Tekrar girince sayaç baştan başlar.

## Komutlar

| Komut | Yetki | Açıklama |
|---|---|---|
| `/afk bakiye` | `afk.use` | AFK paranı gösterir (sadece sen görürsün) |
| `/afk market` | `afk.use` | AFK marketi açar |
| `/afk top` | `afk.use` | En zengin 10 oyuncu |
| `/afk alan oluştur <isim>` | `afk.admin.alan` | Son seçili pos1-pos2'yi AFK alanı yapar |
| `/afk alan sil <isim>` | `afk.admin.alan` | Alanı siler (tab ile aktif alanlar listelenir) |
| `/afk alan liste` | `afk.admin.alan` | Tüm alanlar ve ayarları |
| `/afk alan ayarla <isim> <cooldown\|odul\|vip-odul> <değer>` | `afk.admin.alan` | Oyun içinden ayar (yml'ye yazılır) |
| `/afk market ekle <fiyat> [slot]` | `afk.admin.market` | **Elindeki** itemi markete ekler (sınırsız satış) |
| `/afk market sil <slot>` | `afk.admin.market` | Slottaki ürünü siler (tab'da sadece dolu slotlar çıkar) |
| `/afk market fiyat <slot> <fiyat>` | `afk.admin.market` | Fiyat değiştirir |
| `/afk para ver <oyuncu> <miktar>` | `afk.admin.para` | Oyuncuya AFK parası verir |
| `/afk para al <oyuncu> <miktar>` | `afk.admin.para` | Oyuncudan AFK parası alır |
| `/afk para ayarla <oyuncu> <miktar>` | `afk.admin.para` | Bakiyeyi ayarlar |
| `/afk para bak <oyuncu>` | `afk.admin.para` | Bakiyesine bakar |
| `/afk reload` | `afk.admin.reload` | config.yml, alanlar.yml, afkmarket.yml, bakiyeler.yml yeniden yüklenir |

Takma ad: `/afkalan`. Yönetici alt komutları yetkisi olmayanların tab listesinde ve yardımında **hiç görünmez**.

## Yetkiler

| Yetki | Varsayılan | Açıklama |
|---|---|---|
| `afk.use` | herkes | bakiye, market, top |
| `afk.vipafk` | kimse | Alanlarda `vip-odul` miktarını alır (alan bazında `vip-yetki` ile değiştirilebilir) |
| `afk.admin` | OP | Aşağıdakilerin hepsi |
| `afk.admin.alan` / `.market` / `.para` / `.reload` | OP | Ayrı ayrı verilebilir |

## Dosyalar (oyun ↔ yml çift yönlü)

Komutla yapılan her değişiklik anında dosyaya yazılır. Dosyayı elle düzenleyip `/afk reload` yazarsan oyuna uygulanır.
Komut çalıştırmadan önce dosya elle değiştirilmiş mi diye bakılır; değiştirildiyse önce o hali yüklenir, elle yaptığın düzenleme **ezilmez**.
Dosyada yazım hatası yaparsan eski veriler korunur ve hatanın yeri sana söylenir — hiçbir veri silinmez.

- `config.yml` – mesajlar, sayaç (TITLE/ACTIONBAR), scoreboard satırları, market görünümü, sesler
- `alanlar.yml` – alanlar: `dunya`, `pos1`, `pos2`, `cooldown`, `odul`, `vip-odul`, `vip-yetki`
- `afkmarket.yml` – ürünler (slot 1–21): `fiyat`, `item`
- `bakiyeler.yml` – `oyuncular.<uuid>.isim / bakiye`

Örnek `alanlar.yml`:

```yaml
alanlar:
  deneme:
    dunya: world
    pos1: {x: 0, y: 64, z: 0}
    pos2: {x: 10, y: 70, z: 10}
    cooldown: 60
    odul: 1.0
    vip-odul: 2.0
    vip-yetki: afk.vipafk
```

## Market

45 slot; tüm kenarlar gri cam. Ürünler **2. satır 2. slottan** başlar (21 ürün: `#1`–`#21`).
Ürünün altında fiyat, oyuncunun bakiyesi ve "satın al / yetersiz bakiye" durumu yazar.
Yöneticiler ürünün slot numarasını da görür. Envanter doluysa satış yapılmaz, para düşmez.

## PlaceholderAPI

| Placeholder | Değer |
|---|---|
| `%afk_bakiye%` | Biçimli bakiye (`12.50`) |
| `%afk_bakiye_ham%` | Ham sayı |
| `%afk_para_birimi%` | Para birimi adı |
| `%afk_alan%` | Bulunduğu AFK alanı (yoksa `Yok`) |
| `%afk_alanda%` | `Evet` / `Hayır` |
| `%afk_kalan%` | Ödüle kalan saniye |
| `%afk_cooldown%` | Alanın cooldown süresi |
| `%afk_odul%` | Alandaki ödül miktarı (VIP dahil) |
| `%afk_top_<1-100>_isim%` / `%afk_top_<1-100>_bakiye%` | Sıralama |

## Performans

- Tek bir görev saniyede bir çalışır; `PlayerMoveEvent` dinlenmez.
- Alanlar dünyaya göre indekslenir: alan olmayan dünyadaki oyuncu için maliyet tek bir harita bakışıdır.
  AFK duran oyuncu için önce bulunduğu alan kontrol edilir (tek kutu karşılaştırması). Konum okunurken nesne oluşturulmaz.
- Scoreboard takım öneki ile çizilir: titreme yok, sadece değişen satır gönderilir.
- Bakiyeler bellekte tutulur; dosyaya yazma ayrı bir thread'de, toplu ve atomik yapılır (yarım dosya oluşmaz).
- Config değerleri yüklemede bir kez okunur ve renklendirilir.

## Geliştiriciler için

`com.jestron.afkalan.api.AfkOdulEvent` – ödül verilmeden önce çağrılır; miktar değiştirilebilir veya iptal edilebilir.

## Derleme

```bash
mvn clean package   # JDK 17+ ; çıktı: target/AfkAlan-<sürüm>.jar (Java 8 bytecode)
```

WorldEdit için derleme bağımlılığı yoktur (çalışma anında bağlanır), bu yüzden IDE'de WorldEdit hatası çıkmaz.

> Not: Scoreboard'u paketlerle çizen eklentiler (ör. TAB) kullanıyorsan çakışmayı önlemek için
> `config.yml` içinde `scoreboard.aktif: false` yapabilir, satırları o eklentide `%afk_...%` ile gösterebilirsin.

> Not: Sunucuda EssentialsX varsa onun `/afk` komutuyla çakışmaması için AfkAlan Essentials'tan önce yüklenir.
> Essentials'ın AFK komutu `/away` olarak kullanılmaya devam eder.
