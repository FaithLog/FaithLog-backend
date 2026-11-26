package com.faithlog.companion;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.points.PointTransactionRepository;
import com.faithlog.points.PointWalletService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GardenDecorationShopService {
    private static final Map<String, Integer> PRICES = prices();
    private static final String KEY_PREFIX = "garden-decoration:";
    private final PointWalletService wallet;
    private final PointTransactionRepository transactions;

    public GardenDecorationShopService(PointWalletService wallet, PointTransactionRepository transactions) {
        this.wallet = wallet;
        this.transactions = transactions;
    }

    @Transactional
    public Shop get(Long userId) {
        var balance = wallet.wallet(userId).balance();
        return catalog(userId, balance);
    }

    @Transactional
    public Shop buy(Long userId, String id, int expectedPrice) {
        var price = PRICES.get(id);
        if (price == null) {
            throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
        }
        // The account lock also serializes draws and mission awards. The debit
        // record permanently grants ownership and is unique per user and item.
        long balance = wallet.wallet(userId).balance();
        String key = KEY_PREFIX + id;
        if (transactions.findByUserIdAndBusinessKey(userId, key).isEmpty()) {
            if (price != expectedPrice) {
                throw new BusinessException(ErrorCode.COMPANION_REQUEST_CONFLICT);
            }
            if (balance < price) {
                throw new BusinessException(ErrorCode.COMPANION_INSUFFICIENT_POINTS);
            }
            wallet.settle(userId, -price, key);
            balance -= price;
        }
        return catalog(userId, balance);
    }

    private Shop catalog(Long userId, long balance) {
        var keys = PRICES.keySet().stream().map(id -> KEY_PREFIX + id).toList();
        var owned = Set.copyOf(transactions.findBusinessKeysByUserIdAndBusinessKeyIn(userId, keys));
        var items = PRICES.entrySet().stream()
            .map(item -> new Item(item.getKey(), item.getValue(), owned.contains(KEY_PREFIX + item.getKey())))
            .toList();
        return new Shop(balance, items);
    }

    private static Map<String, Integer> prices() {
        var prices = new LinkedHashMap<String, Integer>();
        add(prices, 100,
            "leaf-cushion", "ceramic-water-bowl", "seed-dish", "ribbon-cabbage",
            "paw-rug", "leaf-stepping-stone", "star-lantern", "honey-pot",
            "rainbow-pinwheel", "pumpkin-lantern", "picnic-hamper", "pastel-kite");
        add(prices, 200,
            "hay-feeder", "dove-perch", "shell-bath", "blanket-basket",
            "mossy-basking-rock", "acorn-birdhouse", "golden-birdbath", "olive-sapling",
            "yellow-flower-trough", "carrot-planter", "blue-watering-can", "lavender-planter",
            "wooden-wheelbarrow", "strawberry-hanging-basket", "succulent-stand", "tulip-boot-planter",
            "leaf-pond", "mushroom-stool", "berry-stump-table", "sunflower-parasol",
            "clover-seat", "blue-rain-barrel", "garden-book-stand", "leaf-umbrella",
            "wooden-bell-arch", "apple-crate", "stone-sundial");
        add(prices, 300,
            "lamb-shelter", "crescent-bed", "hollow-log", "strawberry-pet-house",
            "blossom-arch", "mini-dock", "wave-hammock", "cloud-swing",
            "stone-bridge", "woodland-well", "chapel-ornament");
        return Collections.unmodifiableMap(prices);
    }

    private static void add(Map<String, Integer> prices, int price, String... ids) {
        for (String id : ids) prices.put(id, price);
    }

    public record Item(String id, int price, boolean owned) {}
    public record Shop(long balance, List<Item> items) {}
}
