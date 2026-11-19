# Değişiklikler

## 1.0.0

WorldEdit/FAWE ile seçilen alanı AFK alanı yapar. Alanda duran oyuncunun ekranının ortasında geri sayım döner, süre dolunca sanal **AFK Parası** verilir, sayaç baştan başlar. Para `/afk market` ile harcanır.

**Sürümler:** Spigot / Paper **1.16.5 – 1.21.x**, tek jar (Java 8 bytecode).

### Test edildi (gerçek Paper sunucusu + WorldEdit + PlaceholderAPI)
| Sunucu | Java | WorldEdit |
|---|---|---|
| 1.16.5 | 11 | 7.2.5 |
| 1.17.1 / 1.18.2 / 1.19.4 / 1.20.4 | 17 | 7.2.20 |
| 1.20.6 / 1.21.1 | 21 | 7.3.10 |
| 1.21.4 | 21 | 7.3.19 |
| 1.21.11 | 25 | 7.4.6 |

### Özellikler
- `/afk alan oluştur|sil|liste|ayarla` – pos1/pos2 seçilmediyse uyarır, `alanlar.yml` ile çift yönlü senkron
- Ekranın ortasında (title) geri sayım, alandan çıkınca sıfırlanır; ayrıca ACTIONBAR / IKISI / KAPALI modları
- Alan bazında `cooldown`, `odul`, `vip-odul` (`afk.vipafk`)
- Sadece alan içindeyken görünen titreşimsiz AFK scoreboard'u; çıkınca eski scoreboard geri gelir
- 45 slotluk market (kenarlar gri cam, ürünler 2. satır 2. slottan), `/afk market ekle|sil|fiyat`, tab'da sadece dolu slotlar
- **Yeni:** `/afk para ver|al|ayarla|bak <oyuncu> <miktar>` (çevrimdışı oyuncular dahil), `/afk top`
- PlaceholderAPI: `%afk_bakiye%`, `%afk_alan%`, `%afk_kalan%`, `%afk_top_1_isim%` ...
- Yönetici komutları yetkisi olmayanların tab listesinde ve yardımında görünmez
- `/afk reload` – tüm .yml dosyaları; yml'de yazım hatası olursa eski veri korunur
- Geliştirici API'si: `AfkOdulEvent`

### Performans
Saniyede bir çalışan tek görev, hareket eventi yok, dünya indeksli alan araması, bellek içi bakiye + ayrı thread'de atomik kayıt.

Kurulum: `AfkAlan-1.0.0.jar` dosyasını `plugins/` klasörüne at. Alan oluşturmak için WorldEdit veya FAWE gerekir.
