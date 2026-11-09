$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $mongoScript = @'
const auth = db.getSiblingDB('ecommerce_auth');
const catalog = db.getSiblingDB('ecommerce_catalog');
const customer = db.getSiblingDB('ecommerce_customer');
const orderDb = db.getSiblingDB('ecommerce_order');
const notificationDb = db.getSiblingDB('ecommerce_notification');

const issues = {
  missingCollections: 0,
  orphanRefreshSessions: 0,
  orphanAddresses: 0,
  invalidDefaultAddresses: 0,
  orphanWishlists: 0,
  unknownWishlistProducts: 0,
  orphanOrders: 0,
  unknownOrderProducts: 0,
  orphanNotifications: 0,
  unknownNotificationOrders: 0,
  orphanInventoryProducts: 0,
  negativeAvailability: 0,
  badReservations: 0,
  activeOrdersWithoutReservation: 0
};
for (const [database, names] of [
  [auth, ['users', 'refresh_sessions']],
  [catalog, ['products', 'inventory_variants']],
  [customer, ['shipping_addresses', 'wishlists']],
  [orderDb, ['orders']],
  [notificationDb, ['notifications']]
]) {
  const actual = new Set(database.getCollectionNames());
  for (const name of names) if (!actual.has(name)) issues.missingCollections++;
}

const users = new Set(auth.users.find({}, {_id: 1}).toArray().map(item => String(item._id)));
const products = new Set(catalog.products.find({}, {_id: 1}).toArray().map(item => String(item._id)));
const orders = orderDb.orders.find().toArray();
const orderById = new Map(orders.map(item => [String(item._id), item]));
const reservedStatuses = ['CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

auth.refresh_sessions.find().forEach(item => {
  if (!users.has(item.userId)) issues.orphanRefreshSessions++;
});

const addressCounts = new Map();
const defaultCounts = new Map();
customer.shipping_addresses.find().forEach(item => {
  if (!users.has(item.userId)) issues.orphanAddresses++;
  addressCounts.set(item.userId, (addressCounts.get(item.userId) || 0) + 1);
  if (item.isDefault) defaultCounts.set(item.userId, (defaultCounts.get(item.userId) || 0) + 1);
});
for (const userId of addressCounts.keys()) {
  if ((defaultCounts.get(userId) || 0) !== 1) issues.invalidDefaultAddresses++;
}

customer.wishlists.find().forEach(item => {
  if (!users.has(String(item._id))) issues.orphanWishlists++;
  for (const favorite of item.items || []) {
    if (!products.has(favorite.productId)) issues.unknownWishlistProducts++;
  }
});
for (const item of orders) {
  if (!users.has(item.userId)) issues.orphanOrders++;
  for (const line of item.items || []) {
    if (!products.has(line.productId)) issues.unknownOrderProducts++;
  }
}
notificationDb.notifications.find().forEach(item => {
  if (!users.has(item.userId)) issues.orphanNotifications++;
  if (item.referenceId && !orderById.has(item.referenceId)) issues.unknownNotificationOrders++;
});

const reservedOrderIds = new Set();
catalog.inventory_variants.find().forEach(item => {
  if (!products.has(item.productId)) issues.orphanInventoryProducts++;
  if (item.available < 0) issues.negativeAvailability++;
  for (const [orderId, quantity] of Object.entries(item.reservations || {})) {
    reservedOrderIds.add(orderId);
    const order = orderById.get(orderId);
    if (!order || !reservedStatuses.includes(order.status) || order.inventoryState !== 'RESERVED' || quantity <= 0) issues.badReservations++;
  }
});
for (const item of orders) {
  if (reservedStatuses.includes(item.status) && item.inventoryState === 'RESERVED'
      && !reservedOrderIds.has(String(item._id))) {
    issues.activeOrdersWithoutReservation++;
  }
}

print('database_integrity: ' + JSON.stringify(issues));
if (Object.values(issues).some(count => count > 0)) quit(2);
'@
    docker compose exec -T mongodb mongosh --quiet --eval $mongoScript
    if ($LASTEXITCODE -ne 0) { throw 'Database integrity check failed.' }
} finally {
    Pop-Location
}
