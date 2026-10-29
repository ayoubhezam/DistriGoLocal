# DistriGo: French → Arabic glossary (Phase 0)

**Date:** 2026-10-27 · **Status:** approved by the owner on 2026-10-27, as proposed. Plan: `docs/i18n_rtl_plan.md`.

Every Arabic string in the app is written from this list: one French term, one Arabic term, everywhere. The French
column is the app's own wording, taken from the code. **★** marks a real choice: the proposal is first, the
alternatives after it. Change any line by giving its number and your choice.

## Writing rules

1. **Register:** Modern Standard Arabic, with the words Algerian commerce uses (زبون, وصل, ولاية, بلدية), not
   Middle-Eastern equivalents (عميل, إيصال, محافظة).
2. **Buttons and menu items:** a verbal noun, not an imperative: حفظ، إلغاء، حذف (not احفظ، ألغِ). This also avoids
   choosing a gender for "you".
3. **Messages to the user:** neutral turns that need no gender: « تعذّر الحفظ » rather than « لم تستطع الحفظ ».
   When "you" can't be avoided, use the masculine, as Algerian business apps do.
4. **Digits 0–9** always (already in place), `DA` after amounts (D5), the brand **DistriGo** in Latin letters.
5. **Punctuation:** the Arabic comma « ، » and question mark « ؟ ». Keep « … » guillemets. No arrows inside text: the
   icons carry the direction.
6. **What the user typed** (names, notes, renamed types) is never translated.

## 1. Sections and navigation

| # | French | Arabic | Note |
|---|---|---|---|
| 1 | Dashboard | لوحة القيادة | ★ or « الرئيسية » (shorter, fits the bottom bar better) |
| 2 | Dépôt vente | بيع المستودع | bottom-bar label; in titles: « البيع من المستودع » |
| 3 | Tournées / Tournée | الجولات / جولة | « جولة توزيع » in sentences where needed |
| 4 | Produits | المنتجات | |
| 5 | Achats | المشتريات | |
| 6 | Plus | المزيد | |
| 7 | Clients / Client | الزبائن / زبون | ★ or « العملاء / عميل » (Middle-East usage) |
| 8 | Fournisseurs / Fournisseur | الموردون / مورد | |
| 9 | Rapports | التقارير | |
| 10 | Paramètres | الإعدادات | |
| 11 | Charges | المصاريف | |
| 12 | Pertes | الخسائر | |
| 13 | Inventaire | الجرد | |
| 14 | Stock Camion | مخزون الشاحنة | |
| 15 | Mouvements (de stock) | حركات المخزون | |
| 16 | Compte | الحساب | |
| 17 | Abonnement | الاشتراك | |
| 18 | Mon objectif | هدفي | |
| 19 | Secteurs / Secteur | القطاعات / قطاع | |
| 20 | Quartiers / Quartier | الأحياء / حي | |
| 21 | Corbeille | سلة المحذوفات | |
| 22 | Diagnostic | التشخيص | |
| 23 | Notifications | الإشعارات | |
| 24 | Profil | الملف الشخصي | |
| 25 | Ma sélection | اختياراتي | ★ or « القائمة المختارة » / « السلة » |
| 26 | Listes et catégories | القوائم والفئات | |

## 2. Documents and operations

| # | French | Arabic | Note |
|---|---|---|---|
| 27 | Vente (une vente) | عملية بيع | « بيع » in compounds: « وصل بيع » |
| 28 | Nouvelle vente | بيع جديد | |
| 29 | Vente dépôt / Vente tournée | بيع من المستودع / بيع في الجولة | speed-dial labels |
| 30 | Facture | فاتورة | |
| 31 | Bon (d'achat) | وصل شراء | |
| 32 | Reçu de vente | وصل بيع | ★ or « إيصال بيع » |
| 33 | Achat / Nouvel achat | شراء / شراء جديد | |
| 34 | Brouillon(s) | مسودة / مسودات | |
| 35 | Chargement (du camion) | تحميل الشاحنة | |
| 36 | Décharger le camion | تفريغ الشاحنة | |
| 37 | Retour client | مرتجع زبون | |
| 38 | Retour fournisseur | مرتجع إلى المورد | |
| 39 | Motif | السبب | |
| 40 | Perte (une perte) | خسارة | |
| 41 | Charge (une charge) | مصروف | |
| 42 | Ajustement | تسوية | stock correction |
| 43 | Encaissement | تحصيل | Dashboard tile, collecting a client's debt |
| 44 | Versement | دفعة | |
| 45 | Paiement | دفع | |
| 46 | Mouvement | حركة | |
| 47 | Entrée / Sortie | دخول / خروج | stock movements |
| 48 | Session d'inventaire | جلسة جرد | |
| 49 | Visite (d'un client, tournée) | زيارة | |

## 3. Stock and products

| # | French | Arabic | Note |
|---|---|---|---|
| 50 | Stock | المخزون | |
| 51 | Dépôt | المستودع | ★ or « المخزن » (common in Algeria, but also means "stock") |
| 52 | Camion | الشاحنة | |
| 53 | Stock négatif / Autoriser le stock négatif | مخزون سالب / السماح بالمخزون السالب | |
| 54 | Stock faible | مخزون منخفض | |
| 55 | Rupture de stock | نفاد المخزون | |
| 56 | Stock minimum | الحد الأدنى للمخزون | |
| 57 | Carton / cartons | علبة / علب (2: علبتان) | unit; owner, 2026-10-29 (was كرتونة / كراتين) |
| 57b | Article(s) of a sale (« Articles (3) ») | منتج (1) / المنتجات (n) | owner, 2026-10-29 (was المواد / مادة) |
| 58 | Pièce / pièces | قطعة / قطع | unit |
| 59 | Kg | كغ | unit |
| 60 | Colis | طرد | ★ or « علبة » / « رزمة »: the pack a pièce product is bought in |
| 61 | Unités/colis | وحدة/طرد | follows 60 |
| 62 | Nombre de colis | عدد الطرود | follows 60 |
| 63 | Code-barres | الباركود | ★ or « الرمز الشريطي » (formal) |
| 64 | Scanner un code-barres | مسح الباركود | follows 63 |
| 65 | Catégorie / Sous-catégorie | الفئة / الفئة الفرعية | |
| 66 | Marque | العلامة التجارية | |
| 67 | Date de péremption | تاريخ انتهاء الصلاحية | |
| 68 | Photo du produit | صورة المنتج | |

## 4. Money

| # | French | Arabic | Note |
|---|---|---|---|
| 69 | Montant | المبلغ | |
| 70 | Total | المجموع | |
| 71 | Prix d'achat | سعر الشراء | |
| 72 | Prix de vente | سعر البيع | |
| 73 | Prix unitaire (P.U.) | سعر الوحدة | |
| 74 | Marge / Marge brute | الهامش / الهامش الإجمالي | |
| 75 | Taux de marge | نسبة الهامش | (sale − purchase) ÷ purchase, as the app computes it |
| 76 | Chiffre d'affaires | رقم الأعمال | Algerian usage |
| 77 | Résultat / Bénéfice | النتيجة / الربح | |
| 78 | Panier moyen | متوسط قيمة البيع | ★ or « متوسط السلة » (literal) |
| 79 | Payé | مدفوع | status |
| 80 | Partiel | مدفوع جزئياً | status |
| 81 | Impayé | غير مدفوع | status |
| 82 | À crédit | بالدين | ★ or « بالآجل » (more formal) |
| 83 | Crédit (what clients owe from sales) | الدين | |
| 84 | Créances (owed to us) | الديون المستحقة لنا | |
| 85 | Dettes (we owe) | الديون علينا | |
| 86 | Solde | الرصيد | |
| 87 | Solde initial | الرصيد الافتتاحي | |
| 88 | Reste (à payer) | الباقي | |
| 89 | Réglé | مسدَّد | |
| 90 | Montant payé | المبلغ المدفوع | |
| 91 | Payé à la vente | مدفوع عند البيع | |
| 92 | Format des montants | تنسيق المبالغ | |
| 93 | DA | DA | kept (D5) |

## 5. Mon objectif and commissions

| # | French | Arabic | Note |
|---|---|---|---|
| 94 | Objectif (du mois) | الهدف (الشهري) | |
| 95 | Prime | مكافأة | |
| 96 | Commission | عمولة | |
| 97 | Palier | شريحة | |
| 98 | Seuil | الحد | |
| 99 | Taux | النسبة | |
| 100 | Ventes facturées | المبيعات المفوترة | |
| 101 | Recouvrement | التحصيل | same word as 43, on purpose |
| 102 | Trimestre (4e trimestre) | الثلاثي (الثلاثي الرابع) | ★ Algerian usage; or « الربع الرابع » |

## 6. Clients, places, people

| # | French | Arabic | Note |
|---|---|---|---|
| 103 | Détail (type de client) | تجزئة | |
| 104 | Gros | جملة | |
| 105 | Société | شركة | |
| 106 | Commercial(e) / représentant | مندوب المبيعات | |
| 107 | Wilaya | الولاية | names from `wilayas.json` (D8) |
| 108 | Commune | البلدية | |
| 109 | Adresse | العنوان | |
| 110 | Téléphone | الهاتف | |
| 111 | Position sur la carte | الموقع على الخريطة | |

## 7. Built-in types (shown in Arabic by key, D7)

| # | Key → French | Arabic |
|---|---|---|
| 112 | casse → Casse | كسر |
| 113 | peremption → Péremption | انتهاء الصلاحية |
| 114 | vol → Vol | سرقة |
| 115 | perte_transport → Perte de transport | تلف أثناء النقل |
| 116 | don → Don | هبة |
| 117 | autre → Autre | أخرى |
| 118 | vehicule → Véhicule (carburant, pneus, réparation, vidange, assurance) | المركبة (وقود، عجلات، تصليح، تغيير الزيت، تأمين) |
| 119 | personnel → Personnel (salaire, prime, formation) | المستخدمون (أجرة، مكافأة، تكوين) |
| 120 | bureau → Bureau (loyer, électricité, internet, fournitures) | المكتب (كراء، كهرباء، إنترنت، لوازم) |
| 121 | distribution → Distribution (péage, parking, livraison, emballage) | التوزيع (طريق سيار، موقف، توصيل، تغليف) |
| 122 | achats → Achats (matériel, nettoyage, divers) | مشتريات (عتاد، تنظيف، متفرقات) |
| 123 | divers → Divers (imprévu, autre) | متفرقات (طارئ، أخرى) |

Retour motifs (stored as the French sentence, D7):

| # | French | Arabic |
|---|---|---|
| 124 | Produit défectueux | منتج معيب |
| 125 | Produit périmé | منتج منتهي الصلاحية |
| 126 | Erreur de livraison | خطأ في التسليم |
| 127 | Client insatisfait | زبون غير راضٍ |
| 128 | Produit défectueux — repris par le fournisseur | منتج معيب — استرجعه المورد |
| 129 | Produit défectueux — refusé (perte) | منتج معيب — مرفوض (خسارة) |
| 130 | Produit périmé — repris par le fournisseur | منتج منتهي الصلاحية — استرجعه المورد |
| 131 | Produit périmé — refusé (perte) | منتج منتهي الصلاحية — مرفوض (خسارة) |
| 132 | Erreur de commande | خطأ في الطلبية |
| 133 | Excédent de stock | فائض في المخزون |

## 8. Common actions

| # | French | Arabic |
|---|---|---|
| 134 | Annuler | إلغاء |
| 135 | Enregistrer | حفظ |
| 136 | Supprimer | حذف |
| 137 | Modifier | تعديل |
| 138 | Ajouter | إضافة |
| 139 | Fermer | إغلاق |
| 140 | Confirmer / Valider | تأكيد |
| 141 | Retour | رجوع |
| 142 | Suivant / Précédent | التالي / السابق |
| 143 | Rechercher | بحث |
| 144 | Filtres | التصفية |
| 145 | Trier | ترتيب |
| 146 | Réinitialiser | إعادة التعيين |
| 147 | Réessayer | إعادة المحاولة |
| 148 | Voir tout | عرض الكل |
| 149 | Appliquer | تطبيق |
| 150 | Tout sélectionner | تحديد الكل |
| 151 | Retirer | إزالة |
| 152 | Reprendre / Recommencer | متابعة / البدء من جديد |
| 153 | Partager | مشاركة |
| 154 | Imprimer | طباعة |
| 155 | Exporter / Importer | تصدير / استيراد |
| 156 | Sauvegarde / Restaurer | نسخة احتياطية / استعادة |
| 157 | Changer | تغيير |
| 158 | Corriger | تصحيح |
| 159 | Oui / Non | نعم / لا |

## 9. States and periods

| # | French | Arabic | Note |
|---|---|---|---|
| 160 | En attente | قيد الانتظار | |
| 161 | Actif / Expiré | نشط / منتهٍ | subscription |
| 162 | Essai gratuit | فترة تجريبية مجانية | |
| 163 | Consultation seule | للعرض فقط | license read-only |
| 164 | Obsolète (brouillon) | قديمة | draft out of date |
| 165 | Aucun résultat | لا توجد نتائج | |
| 166 | Erreur inconnue | خطأ غير معروف | |
| 167 | Aujourd'hui / Hier | اليوم / أمس | |
| 168 | 7 derniers jours | آخر 7 أيام | |
| 169 | Ce mois / Mois dernier | هذا الشهر / الشهر الماضي | |
| 170 | Cette année | هذه السنة | |
| 171 | Personnalisé | مخصّص | |
| 172 | Du … au … | من … إلى … | |
| 173 | il y a 3 h / hier / à l'instant | منذ 3 ساعات / أمس / الآن | plurals per Arabic rules |
| 174 | Months | جانفي، فيفري، مارس، أفريل، ماي، جوان، جويلية، أوت، سبتمبر، أكتوبر، نوفمبر، ديسمبر | ★ Algerian (ar-DZ); or يناير… (Egypt/Gulf) |

## Next step

The owner approves the list or changes the ★ lines (or any other line) by number. Then Phase 1 (infrastructure)
starts, which changes nothing visible in French.
