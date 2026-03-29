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
}
