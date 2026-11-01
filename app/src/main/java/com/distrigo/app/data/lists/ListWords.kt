package com.distrigo.app.data.lists

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.distrigo.app.R
import com.distrigo.app.core.locale.AppLanguage
import com.distrigo.app.core.text.AppStrings
import com.distrigo.app.core.text.UiText
import com.distrigo.app.data.trash.TrashKind
import com.distrigo.app.data.trash.Usage
import java.util.Locale

/**
 * The words the Listes et catégories and the Corbeille are said in, in the app's language: what each list is called,
 * its entries counted, the sentences that name one (French says « la catégorie… elle », « le type… il »; Arabic has its
 * own agreements, so each list has its own sentence rather than one built from an article and a pronoun), and the
 * « 12 lignes de vente et 3 mouvements de stock » a refusal lists. Written when they are shown, so they follow the
 * language the app is in at that moment.
 */
object ListWords {

    fun text(@StringRes id: Int, vararg args: Any): String = AppStrings.write(UiText.Res(id, args.toList()))

    fun count(@PluralsRes id: Int, n: Int): String = AppStrings.write(UiText.Plural(id, n))

    /** « 3 catégories », « 3 فئات ». */
    @PluralsRes
    fun countRes(kind: ListKind): Int = when (kind) {
        ListKind.CATEGORIES -> R.plurals.lists_n_categories
        ListKind.SOUS_CATEGORIES -> R.plurals.lists_n_subcategories
        ListKind.MARQUES -> R.plurals.lists_n_brands
        ListKind.CHARGE_TYPES, ListKind.PERTE_TYPES -> R.plurals.lists_n_types
        ListKind.CHARGE_SUBTYPES -> R.plurals.lists_n_subtypes
        ListKind.SECTEURS -> R.plurals.lists_n_secteurs
    }

    @StringRes
    fun labelRes(kind: ListKind): Int = when (kind) {
        ListKind.CATEGORIES -> R.string.lists_label_categories
        ListKind.SOUS_CATEGORIES -> R.string.lists_label_subcategories
        ListKind.MARQUES -> R.string.lists_label_brands
        ListKind.CHARGE_TYPES -> R.string.lists_label_charge_types
        ListKind.CHARGE_SUBTYPES -> R.string.lists_label_charge_subtypes
        ListKind.PERTE_TYPES -> R.string.lists_label_perte_types
        ListKind.SECTEURS -> R.string.lists_label_secteurs
    }

    @StringRes
    fun labelRes(kind: TrashKind): Int = when (kind) {
        TrashKind.PRODUCTS -> R.string.lists_label_products
        TrashKind.CLIENTS -> R.string.lists_label_clients
        TrashKind.SUPPLIERS -> R.string.lists_label_suppliers
        TrashKind.CATEGORIES -> R.string.lists_label_categories
        TrashKind.SOUS_CATEGORIES -> R.string.lists_label_subcategories
        TrashKind.MARQUES -> R.string.lists_label_brands
        TrashKind.CHARGE_TYPES -> R.string.lists_label_charge_types
        TrashKind.CHARGE_SUBTYPES -> R.string.lists_label_charge_subtypes
        TrashKind.PERTE_TYPES -> R.string.lists_label_perte_types
    }

    /** « Nouvelle catégorie ». */
    @StringRes
    fun newRes(kind: ListKind): Int = when (kind) {
        ListKind.CATEGORIES -> R.string.lists_new_category
        ListKind.SOUS_CATEGORIES -> R.string.lists_new_subcategory
        ListKind.MARQUES -> R.string.lists_new_brand
        ListKind.CHARGE_TYPES -> R.string.lists_new_charge_type
        ListKind.CHARGE_SUBTYPES -> R.string.lists_new_charge_subtype
        ListKind.PERTE_TYPES -> R.string.lists_new_perte_type
        ListKind.SECTEURS -> R.string.lists_new_secteur
    }

    /** « Ajouter une catégorie ». */
    @StringRes
    fun addRes(kind: ListKind): Int = when (kind) {
        ListKind.CATEGORIES -> R.string.lists_add_category
        ListKind.SOUS_CATEGORIES -> R.string.lists_add_subcategory
        ListKind.MARQUES -> R.string.lists_add_brand
        ListKind.CHARGE_TYPES -> R.string.lists_add_charge_type
        ListKind.CHARGE_SUBTYPES -> R.string.lists_add_charge_subtype
        ListKind.PERTE_TYPES -> R.string.lists_add_perte_type
        ListKind.SECTEURS -> R.string.lists_add_secteur
    }

    /** « Aucune catégorie pour le moment. ». */
    @StringRes
    fun emptyRes(kind: ListKind): Int = when (kind) {
        ListKind.CATEGORIES -> R.string.lists_empty_category
        ListKind.SOUS_CATEGORIES -> R.string.lists_empty_subcategory
        ListKind.MARQUES -> R.string.lists_empty_brand
        ListKind.CHARGE_TYPES -> R.string.lists_empty_charge_type
        ListKind.CHARGE_SUBTYPES -> R.string.lists_empty_charge_subtype
        ListKind.PERTE_TYPES -> R.string.lists_empty_perte_type
        ListKind.SECTEURS -> R.string.lists_empty_secteur
    }

    /** « La catégorie « X » existe déjà. », with where for the lists kept under a parent. */
    fun exists(kind: ListKind, name: String): String = text(
        when (kind) {
            ListKind.CATEGORIES -> R.string.lists_exists_category
            ListKind.SOUS_CATEGORIES -> R.string.lists_exists_subcategory
            ListKind.MARQUES -> R.string.lists_exists_brand
            ListKind.CHARGE_TYPES -> R.string.lists_exists_charge_type
            ListKind.CHARGE_SUBTYPES -> R.string.lists_exists_charge_subtype
            ListKind.PERTE_TYPES -> R.string.lists_exists_perte_type
            ListKind.SECTEURS -> R.string.lists_exists_secteur
        },
        name,
    )

    /** « Impossible de supprimer la catégorie « X » : elle est utilisée par 12 produits. ». */
    fun inUse(kind: ListKind, name: String, usages: List<Usage>): String = text(
        when (kind) {
            ListKind.CATEGORIES -> R.string.lists_in_use_category
            ListKind.SOUS_CATEGORIES -> R.string.lists_in_use_subcategory
            ListKind.MARQUES -> R.string.lists_in_use_brand
            ListKind.CHARGE_TYPES -> R.string.lists_in_use_charge_type
            ListKind.CHARGE_SUBTYPES -> R.string.lists_in_use_charge_subtype
            ListKind.PERTE_TYPES -> R.string.lists_in_use_perte_type
            ListKind.SECTEURS -> R.string.lists_in_use_secteur
        },
        name, and(usages.map { it.toString() }),
    )

    /** « La catégorie n'existe plus : elle a peut-être été supprimée. ». */
    fun gone(kind: ListKind): String = text(
        when (kind) {
            ListKind.CATEGORIES -> R.string.lists_gone_category
            ListKind.SOUS_CATEGORIES -> R.string.lists_gone_subcategory
            ListKind.MARQUES -> R.string.lists_gone_brand
            ListKind.CHARGE_TYPES -> R.string.lists_gone_charge_type
            ListKind.CHARGE_SUBTYPES -> R.string.lists_gone_charge_subtype
            ListKind.PERTE_TYPES -> R.string.lists_gone_perte_type
            ListKind.SECTEURS -> R.string.lists_gone_secteur
        }
    )

    /** « a, b et c », « a، b وc ». */
    fun and(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts.single()
        else -> text(
            R.string.lists_and,
            parts.dropLast(1).joinToString(if (AppLanguage.of(Locale.getDefault()) == AppLanguage.ARABIC) "، " else ", "),
            parts.last(),
        )
    }

    /** A [Usage]'s words: its French one names it; each the app writes has its plurals. */
    @PluralsRes
    internal fun usageRes(singular: String): Int? = USAGES[singular]

    private val USAGES = mapOf(
        "produit" to R.plurals.common_products_count,
        "client" to R.plurals.common_clients_count,
        "tournée" to R.plurals.tournees_count,
        "quartier" to R.plurals.rapports_n_quartiers,
        "vente" to R.plurals.ventes_count,
        "charge" to R.plurals.charges_count,
        "perte" to R.plurals.pertes_count,
        "retour" to R.plurals.rapports_returns_n,
        "paiement" to R.plurals.lists_n_payments,
        "bon d'achat" to R.plurals.lists_n_purchase_bons,
        "brouillon de vente" to R.plurals.lists_n_sale_drafts,
        "brouillon de tournée" to R.plurals.lists_n_tournee_drafts,
        "brouillon d'achat" to R.plurals.lists_n_purchase_drafts,
        "brouillon de chargement" to R.plurals.lists_n_loading_drafts,
        "ligne d'achat" to R.plurals.lists_n_purchase_lines,
        "ligne d'inventaire" to R.plurals.lists_n_inventory_lines,
        "ligne de chargement" to R.plurals.lists_n_loading_lines,
        "ligne de vente" to R.plurals.lists_n_sale_lines,
        "mouvement de stock" to R.plurals.lists_n_stock_movements,
        "retour client" to R.plurals.lists_n_client_returns,
        "retour fournisseur" to R.plurals.lists_n_supplier_returns,
        "sous-catégorie" to R.plurals.lists_n_subcategories,
        "sous-type" to R.plurals.lists_n_subtypes,
    )
}
