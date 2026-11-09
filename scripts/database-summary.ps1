$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $mongoScript = @'
const names = ['ecommerce_auth', 'ecommerce_catalog', 'ecommerce_customer',
               'ecommerce_order', 'ecommerce_notification'];
for (const name of names) {
  const database = db.getSiblingDB(name);
  const collections = database.getCollectionNames().sort();
  print(name + ': ' + (collections.length ? collections.map(collection =>
    collection + '=' + database.getCollection(collection).countDocuments({})).join(', ') : '(empty)'));
}
const orders = db.getSiblingDB('ecommerce_order').orders;
if (db.getSiblingDB('ecommerce_order').getCollectionNames().includes('orders')) {
  print('order_status: ' + JSON.stringify(orders.aggregate([
    {$group: {_id: '$status', count: {$sum: 1}}}, {$sort: {_id: 1}}
  ]).toArray()));
}
const catalog = db.getSiblingDB('ecommerce_catalog');
if (catalog.getCollectionNames().includes('inventory_variants')) {
  const variants = catalog.inventory_variants.find({}, {available: 1, reservations: 1}).toArray();
  print('inventory: variants=' + variants.length +
    ', available=' + variants.reduce((sum, item) => sum + (item.available || 0), 0) +
    ', reservedVariants=' + variants.filter(item =>
      Object.keys(item.reservations || {}).length > 0).length);
}
'@
    docker compose exec -T mongodb mongosh --quiet --eval $mongoScript
    if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc MongoDB Docker.' }
    $cartKeys = @(docker compose exec -T redis redis-cli --scan --pattern 'cart:*')
    if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc Redis Docker.' }
    Write-Output "redis_carts: $($cartKeys.Count)"
} finally {
    Pop-Location
}
