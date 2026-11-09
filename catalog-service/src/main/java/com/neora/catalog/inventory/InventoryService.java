package com.neora.catalog.inventory;

import com.neora.catalog.model.Product;
import com.neora.catalog.flashsale.FlashSaleAllocationService;
import com.neora.catalog.repository.ProductRepository;
import com.neora.shared.error.ConflictException;
import com.neora.shared.error.NotFoundException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {
    private final MongoTemplate mongo;
    private final ProductRepository products;
    private final FlashSaleAllocationService flashSales;

    public InventoryService(MongoTemplate mongo, ProductRepository products, FlashSaleAllocationService flashSales) {
        this.mongo = mongo;
        this.products = products;
        this.flashSales = flashSales;
    }

    public Availability availability(String productId, String size, String color) {
        validateVariant(productId, size, color);
        InventoryVariant stock = mongo.findById(variantId(productId, size, color), InventoryVariant.class);
        return new Availability(productId, size, color, stock == null ? 0 : stock.available());
    }

    public void reserve(String orderId, List<Line> requested) {
        validateOrderId(orderId);
        List<Line> lines = normalize(requested);
        List<SaleLine> sales = saleLines(lines);
        try {
            for (Line line : lines) validateVariant(line.productId(), line.size(), line.color());
            for (Line line : lines) reserveLine(orderId, line);
            for (SaleLine sale : sales) {
                flashSales.reserve(orderId, sale.campaignId(), sale.productId(), sale.quantity(), sale.unitPrice());
            }
        } catch (RuntimeException failed) {
            try {
                release(orderId, lines);
            } catch (RuntimeException releaseFailed) {
                failed.addSuppressed(releaseFailed);
                throw new IllegalStateException("Không thể hoàn trả tồn kho sau lỗi giữ hàng", failed);
            }
            throw failed;
        }
    }

    public void release(String orderId, List<Line> requested) {
        validateOrderId(orderId);
        List<Line> lines = normalize(requested);
        List<SaleLine> sales = saleLines(lines);
        for (Line line : lines) {
            String field = "reservations." + orderId;
            mongo.updateFirst(Query.query(Criteria.where("_id").is(variantId(line.productId(), line.size(), line.color()))
                            .and(field).is(line.quantity())),
                    new Update().inc("available", line.quantity()).unset(field), InventoryVariant.class);
        }
        for (SaleLine sale : sales) {
            flashSales.release(orderId, sale.campaignId(), sale.productId(), sale.quantity());
        }
    }

    public void finalizeReservation(String orderId, List<Line> requested) {
        validateOrderId(orderId);
        List<Line> lines = normalize(requested);
        List<SaleLine> sales = saleLines(lines);
        for (Line line : lines) {
            String id = variantId(line.productId(), line.size(), line.color());
            String field = "reservations." + orderId;
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(id)
                            .and(field).is(line.quantity())),
                    new Update().unset(field), InventoryVariant.class);
            if (result.getModifiedCount() == 0) {
                InventoryVariant stock = mongo.findById(id, InventoryVariant.class);
                if (stock == null) throw new NotFoundException("Không tìm thấy tồn kho của biến thể sản phẩm");
                if (stock.reservations() != null
                        && stock.reservations().containsKey(orderId)) {
                    throw new ConflictException("Số lượng hàng đã giữ không khớp với đơn hàng");
                }
            }
        }
        for (SaleLine sale : sales) {
            flashSales.finalizeReservation(orderId, sale.campaignId(), sale.productId(), sale.quantity());
        }
    }

    private void reserveLine(String orderId, Line line) {
        String id = variantId(line.productId(), line.size(), line.color());
        String field = "reservations." + orderId;
        InventoryVariant stock = mongo.findById(id, InventoryVariant.class);
        if (alreadyReserved(stock, orderId, line.quantity())) return;
        if (stock != null && stock.reservations() != null && stock.reservations().containsKey(orderId)) {
            throw new ConflictException("Đơn hàng đã giữ số lượng khác cho biến thể sản phẩm");
        }
        Query available = Query.query(Criteria.where("_id").is(id).and("available").gte(line.quantity())
                .and(field).exists(false));
        InventoryVariant updated = mongo.findAndModify(available,
                new Update().inc("available", -line.quantity()).set(field, line.quantity()), InventoryVariant.class);
        if (updated != null || alreadyReserved(mongo.findById(id, InventoryVariant.class), orderId, line.quantity())) return;
        throw new ConflictException("Sản phẩm đã hết hàng hoặc không đủ số lượng");
    }

    private boolean alreadyReserved(InventoryVariant stock, String orderId, int quantity) {
        return stock != null && stock.reservations() != null
                && Integer.valueOf(quantity).equals(stock.reservations().get(orderId));
    }

    private List<Line> normalize(List<Line> requested) {
        if (requested == null || requested.isEmpty()) throw new IllegalArgumentException("Danh sách sản phẩm trống");
        Map<String, Line> merged = new java.util.LinkedHashMap<>();
        for (Line line : requested) {
            if (line == null || line.productId() == null || line.productId().isBlank()
                    || line.size() == null || line.size().isBlank()
                    || line.color() == null || line.color().isBlank()
                    || line.quantity() < 1 || line.quantity() > 99) {
                throw new IllegalArgumentException("Biến thể hoặc số lượng sản phẩm không hợp lệ");
            }
            String id = variantId(line.productId(), line.size(), line.color());
            Line existing = merged.get(id);
            if (existing != null && (!java.util.Objects.equals(existing.flashSaleId(), line.flashSaleId())
                    || !java.util.Objects.equals(existing.unitPrice(), line.unitPrice()))) {
                throw new IllegalArgumentException("Giá hoặc chương trình Flash Sale không nhất quán");
            }
            if (line.flashSaleId() != null && (line.flashSaleId().isBlank() || line.unitPrice() == null)) {
                throw new IllegalArgumentException("Thông tin Flash Sale không hợp lệ");
            }
            int quantity = existing == null ? line.quantity() : existing.quantity() + line.quantity();
            if (quantity > 99) throw new IllegalArgumentException("Số lượng sản phẩm vượt quá 99");
            merged.put(id, new Line(line.productId(), line.size(), line.color(), quantity,
                    line.flashSaleId(), line.unitPrice()));
        }
        return new ArrayList<>(merged.values());
    }

    private List<SaleLine> saleLines(List<Line> lines) {
        Map<String, SaleLine> merged = new java.util.LinkedHashMap<>();
        for (Line line : lines) {
            if (line.flashSaleId() == null) continue;
            String key = line.flashSaleId() + "\u0000" + line.productId();
            SaleLine existing = merged.get(key);
            if (existing != null && existing.unitPrice().compareTo(line.unitPrice()) != 0) {
                throw new IllegalArgumentException("Giá Flash Sale không nhất quán trong đơn hàng");
            }
            merged.put(key, new SaleLine(line.flashSaleId(), line.productId(),
                    line.quantity() + (existing == null ? 0 : existing.quantity()), line.unitPrice()));
        }
        return new ArrayList<>(merged.values());
    }

    private void validateVariant(String productId, String size, String color) {
        Product product = products.findById(productId).filter(Product::active)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm"));
        if (product.availableSizes() == null || !product.availableSizes().contains(size)
                || product.colorNames() == null || !product.colorNames().contains(color)) {
            throw new IllegalArgumentException("Biến thể sản phẩm không hợp lệ");
        }
    }

    private void validateOrderId(String orderId) {
        if (orderId == null || !orderId.matches("ord_[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Mã đơn hàng không hợp lệ");
        }
    }

    public static String variantId(String productId, String size, String color) {
        try {
            byte[] data = (productId + "\u0000" + size + "\u0000" + color).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }

    public record Line(String productId, String size, String color, int quantity,
                       String flashSaleId, java.math.BigDecimal unitPrice) {
        public Line(String productId, String size, String color, int quantity) {
            this(productId, size, color, quantity, null, null);
        }
    }
    public record Availability(String productId, String size, String color, int available) {}
    private record SaleLine(String campaignId, String productId, int quantity,
                            java.math.BigDecimal unitPrice) {}
}
