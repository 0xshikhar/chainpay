#!/bin/sh
set -e

ETH_RPC_URL=${ETH_RPC_URL:-"http://anvil:8545"}
PRIVATE_KEY=${PRIVATE_KEY:-"0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"}

echo "⏳ Waiting for Anvil EVM node at $ETH_RPC_URL..."
until nc -z $(echo $ETH_RPC_URL | sed -e 's|http://||' -e 's|:[0-9]*||') $(echo $ETH_RPC_URL | sed -e 's|.*:||'); do
  sleep 1
done

echo "🚀 Deploying ChainPayGateway.sol contract onto EVM Node..."
forge create contracts/ChainPayGateway.sol:ChainPayGateway \
  --rpc-url "$ETH_RPC_URL" \
  --private-key "$PRIVATE_KEY" \
  --broadcast

echo "✅ Smart contract deployment complete!"
