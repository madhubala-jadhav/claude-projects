package org.example.normalize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F6 is a frozen contract, so these are regression locks rather than ordinary unit tests: every
 * assertion here is a key that a saved correction may one day be filed under, and changing one
 * after Slice 5 ships orphans that correction.
 */
class MerchantKeyTest {

    // ---- the three worked examples in the architecture docs -----------------

    @Test
    void reproducesTheComponentsDocPosExample() {
        assertEquals("SWIGGY", MerchantKey.of("POS SWIGGY*ORDER 4471 BANGALORE IN"));
    }

    @Test
    void reproducesTheComponentsDocUpiExample() {
        assertEquals("AMAZON PAY", MerchantKey.of("UPI/AMAZON PAY/9928311/PAYMENT"));
    }

    @Test
    void reproducesTheDataModelNeftExample() {
        // 02-data-model.md §3.7's own transactions.csv sample. The bank reference, the DR
        // marker and the month suffix are all noise around two real words.
        assertEquals("RENT TRANSFER", MerchantKey.of("NEFT DR-HDFC0001234-RENT TRANSFER-AUG"));
    }

    // ---- failures the real HDFC statement exposed ---------------------------

    @Test
    void takesTheMerchantAfterAnAggregatorTagNotTheTagItself() {
        // "IND*" is a payment aggregator. Keying on it would collapse every aggregator-billed
        // merchant on the statement into one key, so a correction to any of them would silently
        // recategorise all the others.
        assertEquals("ADOBE", MerchantKey.of("IND*ADOBEhttps://www."));
        assertEquals("BOOKMYSHOW", MerchantKey.of("PAYU*BOOKMYSHOW"));
    }

    @Test
    void stillTakesTheMerchantBeforeTheStarWhenItIsTheMerchant() {
        // The distinguishing signal is the length and identity of the pre-star token, not the
        // presence of a star: SWIGGY names the merchant, IND does not.
        assertEquals("SWIGGY", MerchantKey.of("SWIGGY*ORDER 4471"));
        assertEquals("ZOMATO", MerchantKey.of("ZOMATO*ORDER 8821 BANGALORE IN"));
    }

    @Test
    void separatesAMerchantWeldedToItsCityByAMissingSpace() {
        // PDF extraction drops the space between two table cells. The lower-to-upper boundary
        // is the only remaining evidence that these were two words.
        assertEquals("BIGBASKET CHENNAI", MerchantKey.of("BigbasketCHENNAI"));
        assertEquals("QUICK SERVICES JAIPUR", MerchantKey.of("QUICK ServicesJAIPUR"));
    }

    @Test
    void stripsUrlFragmentsPickedUpFromCardStatements() {
        assertEquals("NETFLIX", MerchantKey.of("NETFLIX https://netflix.com/billing"));
        assertEquals("SPOTIFY", MerchantKey.of("SPOTIFY www.spotify.com"));
    }

    // ---- the documented normalisation rules ---------------------------------

    @Test
    void stripsEveryPaymentRailPrefix() {
        assertEquals("RENT PAYMENT", MerchantKey.of("NEFT/RENT PAYMENT/1234567"));
        assertEquals("HOME LOAN EMI", MerchantKey.of("ACH DR HOME LOAN EMI"));
        assertEquals("JOHN DOE", MerchantKey.of("IMPS/JOHN DOE/998877"));
    }

    @Test
    void isCaseInsensitiveAndUppercasesTheResult() {
        assertEquals("ZOMATO", MerchantKey.of("pos zomato*order 8821 bangalore in"));
    }

    @Test
    void dropsReferenceNumbersDatesAndPurelyNumericTokens() {
        assertEquals("APOLLO PHARMACY BLR", MerchantKey.of("POS APOLLO PHARMACY BLR"));
        assertEquals("RANDOMSHOP BLR", MerchantKey.of("POS RANDOMSHOP XYZ12345 BLR"));
        assertEquals("ACME PAYROLL SALARY", MerchantKey.of("NEFT CR-ACME PAYROLL-SALARY AUG 2026"));
    }

    @Test
    void keepsAtMostThreeSignificantTokens() {
        assertEquals("ONE TWO THREE", MerchantKey.of("ONE TWO THREE FOUR FIVE"));
    }

    @Test
    void keepsShortDigitRunsThatArePartOfARealName() {
        // Three digits or fewer is a brand, not a reference: 7ELEVEN must stay itself.
        assertEquals("7ELEVEN", MerchantKey.of("POS 7ELEVEN"));
    }

    // ---- shape guarantees ---------------------------------------------------

    @Test
    void neverExceedsSixtyCharacters() {
        String key = MerchantKey.of("SUPERCALIFRAGILISTIC EXPIALIDOCIOUSNESS ESTABLISHMENTARIANISM");

        assertTrue(key.length() <= 60, "02-data-model.md §1.1 caps merchant_key at 60 chars: " + key.length());
    }

    @Test
    void degradesToSomethingRatherThanNothing() {
        // A description made entirely of noise still has to produce a usable key, or the
        // correction has nothing to attach to.
        assertEquals("", MerchantKey.of(null));
        assertEquals("", MerchantKey.of("   "));
        assertEquals("12345", MerchantKey.of("12345"));
    }

    @Test
    void isStableAcrossTheReferenceNumberThatChangesEveryMonth() {
        // This is AC4 in one assertion: the same merchant next month, with a different order
        // number, must land on the same key or the correction does not carry over.
        assertEquals(MerchantKey.of("POS SWIGGY*ORDER 4471 BANGALORE IN"),
                MerchantKey.of("POS SWIGGY*ORDER 9983 BANGALORE IN"));
        assertEquals(MerchantKey.of("UPI/BIGBASKET/9928311/PAYMENT"),
                MerchantKey.of("UPI/BIGBASKET/1102847/PAYMENT"));
    }
}
