// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title ChainPayGateway
 * @notice Enterprise Payment Gateway Router for ChainPay Network
 * @dev Dispatches batch payouts, ERC-20 token transfers, and emits indexed on-chain events for transaction accounting.
 */
contract ChainPayGateway {

    address public owner;

    event PayoutDispatched(
        bytes32 indexed payoutId,
        address indexed merchant,
        address indexed asset,
        address recipient,
        uint256 amount,
        string memo,
        uint256 timestamp
    );

    modifier onlyOwner() {
        require(msg.sender == owner, "ChainPayGateway: caller is not the owner");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    event BatchPayoutDispatched(
        bytes32 indexed batchId,
        uint256 totalCount,
        uint256 totalAmount,
        uint256 timestamp
    );

    /**
     * @notice Dispatch native ETH payout with on-chain reference memo
     */
    function dispatchNativePayout(
        bytes32 payoutId,
        address payable recipient,
        string calldata memo
    ) external payable onlyOwner {
        require(msg.value > 0, "ChainPayGateway: invalid transfer amount");
        (bool success, ) = recipient.call{value: msg.value}("");
        require(success, "ChainPayGateway: transfer failed");

        emit PayoutDispatched(
            payoutId,
            msg.sender,
            address(0),
            recipient,
            msg.value,
            memo,
            block.timestamp
        );
    }

    /**
     * @notice Dispatch batch Native ETH payouts in a single transaction
     */
    function dispatchBatchPayout(
        bytes32 batchId,
        bytes32[] calldata payoutIds,
        address payable[] calldata recipients,
        uint256[] calldata amounts,
        string[] calldata memos
    ) external payable onlyOwner {
        require(payoutIds.length == recipients.length && recipients.length == amounts.length && amounts.length == memos.length, "ChainPayGateway: array length mismatch");
        require(payoutIds.length > 0, "ChainPayGateway: empty batch");

        uint256 totalDispatched = 0;

        for (uint256 i = 0; i < recipients.length; i++) {
            require(amounts[i] > 0, "ChainPayGateway: invalid transfer amount");
            (bool success, ) = recipients[i].call{value: amounts[i]}("");
            require(success, "ChainPayGateway: batch transfer failed");

            totalDispatched += amounts[i];

            emit PayoutDispatched(
                payoutIds[i],
                msg.sender,
                address(0),
                recipients[i],
                amounts[i],
                memos[i],
                block.timestamp
            );
        }

        require(msg.value >= totalDispatched, "ChainPayGateway: insufficient msg.value for batch");

        emit BatchPayoutDispatched(
            batchId,
            recipients.length,
            totalDispatched,
            block.timestamp
        );
    }
}
