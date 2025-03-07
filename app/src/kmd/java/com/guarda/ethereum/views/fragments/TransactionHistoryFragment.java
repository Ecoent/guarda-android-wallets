package com.guarda.ethereum.views.fragments;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.widget.NestedScrollView;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gravilink.zcash.WalletCallback;
import com.gravilink.zcash.ZCashException;
import com.gravilink.zcash.ZCashTransaction_taddr;
import com.gravilink.zcash.ZCashWalletManager;
import com.gravilink.zcash.crypto.Utils;
import com.guarda.ethereum.BuildConfig;
import com.guarda.ethereum.GuardaApp;
import com.guarda.ethereum.R;
import com.guarda.ethereum.customviews.RateDialog;
import com.guarda.ethereum.managers.BitcoinNodeManager;
import com.guarda.ethereum.managers.Callback;
import com.guarda.ethereum.managers.CoinmarketcapHelper;
import com.guarda.ethereum.managers.EthereumNetworkManager;
import com.guarda.ethereum.managers.NetworkManager;
import com.guarda.ethereum.managers.RawNodeManager;
import com.guarda.ethereum.managers.SharedManager;
import com.guarda.ethereum.managers.TransactionsManager;
import com.guarda.ethereum.managers.WalletCreationCallback;
import com.guarda.ethereum.managers.WalletManager;
import com.guarda.ethereum.models.constants.Common;
import com.guarda.ethereum.models.constants.Extras;
import com.guarda.ethereum.models.items.BtgBalanceResponse;
import com.guarda.ethereum.models.items.BtgTxListResponse;
import com.guarda.ethereum.models.items.BtgTxResponse;
import com.guarda.ethereum.models.items.KmdTxListResponse;
import com.guarda.ethereum.models.items.QtumTxListResponse;
import com.guarda.ethereum.models.items.RespExch;
import com.guarda.ethereum.models.items.ResponseExchangeAmount;
import com.guarda.ethereum.models.items.SendRawTxResponse;
import com.guarda.ethereum.models.items.TransactionItem;
import com.guarda.ethereum.rest.ApiMethods;
import com.guarda.ethereum.rest.RequestorBtc;
import com.guarda.ethereum.utils.CurrencyUtils;
import com.guarda.ethereum.views.activity.MainActivity;
import com.guarda.ethereum.views.activity.SendingCurrencyActivity;
import com.guarda.ethereum.views.activity.TransactionDetailsActivity;
import com.guarda.ethereum.views.adapters.TransHistoryAdapter;
import com.guarda.ethereum.views.fragments.base.BaseFragment;

import org.bitcoinj.core.Coin;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import okhttp3.ResponseBody;

import static com.guarda.ethereum.models.constants.Common.BLOCK;
import static com.guarda.ethereum.models.constants.Common.EXTRA_TRANSACTION_POSITION;
import static com.guarda.ethereum.models.constants.Common.KMD_MIN_CONFIRM;
import static com.guarda.ethereum.models.constants.Extras.CREATE_WALLET;
import static com.guarda.ethereum.models.constants.Extras.FIRST_ACTION_MAIN_ACTIVITY;
import static com.guarda.ethereum.models.constants.Extras.KEY;

@AutoInjector(GuardaApp.class)
public class TransactionHistoryFragment extends BaseFragment {

    @BindView(R.id.tv_wallet_count)
    TextView tvCryptoCount;
    @BindView(R.id.tv_wallet_usd_count)
    TextView tvUSDCount;
    @BindView(R.id.fab_menu)
    FloatingActionMenu fabMenu;
    @BindView(R.id.fab_buy)
    FloatingActionButton fabBuy;
    @BindView(R.id.fab_purchase)
    FloatingActionButton fabPurchase;
    @BindView(R.id.fab_deposit)
    FloatingActionButton fabDeposit;
    @BindView(R.id.fab_withdraw)
    FloatingActionButton fabWithdraw;
    @BindView(R.id.iv_update_transactions)
    ImageView tvUpdateTransactions;
    @BindView(R.id.rv_transactions_list)
    RecyclerView rvTransactionsList;
    @BindView(R.id.sv_main_scroll_layout)
    NestedScrollView nsvMainScrollLayout;
    @BindView(R.id.swipeRefresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.interestClaimed)
    TextView tvInterestClaimed;
    @BindView(R.id.youHaveToClaim)
    TextView tvYouHaveToClaim;
    @BindView(R.id.youHaveToClaimInterest)
    TextView tvYouHaveToClaimInterest;
    @BindView(R.id.btnClaimNow)
    Button btnClaimNow;

    private static final String BLANK_BALANCE = "...";
    private boolean isVisible = true;
    private boolean stronglyHistory = false;
    private Handler handler = new Handler();
    private ObjectAnimator loaderAnimation;

    private long curInterest = 0;

    @Inject
    WalletManager walletManager;

    @Inject
    EthereumNetworkManager networkManager;

    @Inject
    TransactionsManager transactionsManager;

    @Inject
    SharedManager sharedManager;

    @Inject
    RawNodeManager mNodeManager;

    public TransactionHistoryFragment() {
        GuardaApp.getAppComponent().inject(this);
    }

    @Override
    protected int getLayout() {
        return R.layout.fragment_transaction_history;
    }

    @Override
    protected void init() {
        stronglyHistory = true;

        nsvMainScrollLayout.smoothScrollTo(0, 0);
        setCryptoBalance(BLANK_BALANCE);

        setUSDBalance("...");

        fabMenu.setClosedOnTouchOutside(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            initFabHider();
        }

        initRotation(tvUpdateTransactions);
        initMenuButton();

        swipeRefreshLayout.setProgressViewEndTarget(false, -2000);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipeRefreshLayout.setRefreshing(false);
                showBalance(false);
            }
        });

        String firstAction = null;
        if (getArguments() != null) {
            firstAction = getArguments().getString(FIRST_ACTION_MAIN_ACTIVITY);
        }
        if (firstAction != null && firstAction.equalsIgnoreCase(CREATE_WALLET)) {
            if (TextUtils.isEmpty(walletManager.getWalletFriendlyAddress())) {
                createWallet(BLOCK);
            }
        } else {
            if (firstAction != null && firstAction.equalsIgnoreCase(Extras.SHOW_STRICTLY_HISTORY)) {
                stronglyHistory = true;
            }
            checkFromRestore();
        }

        tvYouHaveToClaim.setVisibility(View.GONE);
        tvYouHaveToClaimInterest.setVisibility(View.GONE);

        Fragment thisFragment = this;

        btnClaimNow.setOnClickListener((l) -> {
            try {
                if (btnClaimNow.getText().equals("OK")) {
                    onUpdateClick();
                } else {
                    btnClaimNow.setEnabled(false);

                    // Here is call from Zcash library for supporting Sapling update, because Komodo is Zcash's fork
                    ZCashWalletManager.getInstance().createClaimTransaction_taddr(walletManager.getWalletFriendlyAddress(),
                            walletManager.getWalletFriendlyAddress(),
                            walletManager.getMyBalance().getValue(),
                            0L,
                            walletManager.anyToWif(walletManager.getPrivateKey()),
                            KMD_MIN_CONFIRM,
                            curInterest, new WalletCallback<String, ZCashTransaction_taddr>() {
                                @Override
                                public void onResponse(String r1, ZCashTransaction_taddr r2) {
                                    Log.i("RESPONSE CODE", r1);
                                    if (r1.equals("ok")) {
                                        try {
                                            String lastTxhex = Utils.bytesToHex(r2.getBytes());
                                            Log.i("lastTxhex", lastTxhex);
                                            BitcoinNodeManager.sendTransaction(lastTxhex, new ApiMethods.RequestListener() {
                                                @Override
                                                public void onSuccess(Object response) {
                                                    SendRawTxResponse res = (SendRawTxResponse) response;
                                                    Log.d("TX_RES", "res " + res.getHashResult() + " error " + res.getError());

                                                    try {
                                                        thisFragment.getActivity().runOnUiThread(() -> {
                                                            tvInterestClaimed.setVisibility(View.VISIBLE);
                                                            tvInterestClaimed.setText("Rewards claimed");
                                                            tvYouHaveToClaim.setVisibility(View.GONE);
                                                            tvYouHaveToClaimInterest.setVisibility(View.GONE);
                                                            btnClaimNow.setEnabled(true);
                                                            btnClaimNow.setAlpha(1.0f);
                                                            btnClaimNow.setText("OK");
                                                        });
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                @Override
                                                public void onFailure(String msg) {
                                                    try {
                                                        thisFragment.getActivity().runOnUiThread(() -> {
                                                            Toast.makeText(thisFragment.getActivity(), CurrencyUtils.getBtcLikeError(msg), Toast.LENGTH_SHORT).show();
                                                            btnClaimNow.setEnabled(true);
                                                        });
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    Log.d("svcom", "failure - " + msg);
                                                }
                                            });
                                        } catch (ZCashException e) {
                                            ((MainActivity) getActivity()).showCustomToast("Error: " + e.getMessage(), R.drawable.err_network);
                                            e.printStackTrace();
                                            Log.i("TX", "Cannot sign transaction");
                                        }
                                    } else {
                                        ((MainActivity) getActivity()).showCustomToast("Can not create the transaction. Check arguments", R.drawable.err_network);
                                        Log.i("psd", "createTransaction_taddr: RESPONSE CODE is not ok");
                                    }
                                }
                            });
                }
            } catch (Exception e) {
                ((MainActivity) getActivity()).showCustomToast(getStringIfAdded(R.string.err_claim), R.drawable.err_network);
                btnClaimNow.setEnabled(true);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void initFabHider() {
        nsvMainScrollLayout.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (scrollY > oldScrollY) {
                    fabMenu.setVisibility(View.GONE);
                } else {
                    fabMenu.setVisibility(View.VISIBLE);
                }
            }
        });
    }


    @Override
    public void onResume() {
        super.onResume();
        if (isWalletExist()) {
            showBalance(true);
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacksAndMessages(null);

        super.onPause();
    }

    private void createWallet(String passphrase) {
        showProgress(getStringIfAdded(R.string.generating_wallet));
        walletManager.createWallet(passphrase, new WalletCreationCallback() {
            @Override
            public void onWalletCreated(Object walletFile) {
                closeProgress();
                openUserWalletFragment();
            }
        });
    }

    private void showTransactions() {
        initTransactionHistoryRecycler();
        loaderAnimation.cancel();
        if (transactionsManager.getTransactionsList().size() == 0) {
            GuardaApp.isTransactionsEmpty = true;
            openUserWalletFragment();
        } else {
            GuardaApp.isTransactionsEmpty = false;
            updateTxsCache();
        }
    }

    private void openUserWalletFragment() {
        if (!stronglyHistory) {
            navigateToFragment(new UserWalletFragment());
        }
    }

    private boolean isWalletExist() {
        return !TextUtils.isEmpty(walletManager.getWalletFriendlyAddress());
    }

    private void showBalance(boolean withCache) {
        if (isAdded() && !isDetached() && isVisible && NetworkManager.isOnline(getActivity())) {
            if (withCache)
                loadFromCache();

            startClockwiseRotation();
            loadBalance();
            loadTransactions();
            askRating();
        } else {
            ((MainActivity) getActivity()).showCustomToast(getStringIfAdded(R.string.err_network), R.drawable.err_network);
        }
    }

    private void loadFromCache() {
        String jsonFromPref = sharedManager.getTxsCache();
        if (!jsonFromPref.equalsIgnoreCase("")) {
            Gson gson = new Gson();
            Type listType = new TypeToken<Map<String, ArrayList<TransactionItem>>>(){}.getType();
            Map<String, ArrayList<TransactionItem>> addrTxsMap = gson.fromJson(jsonFromPref, listType);
            ArrayList<TransactionItem> txList = addrTxsMap.get(walletManager.getWalletFriendlyAddress());
            if (txList != null) {
                transactionsManager.setTransactionsList(txList);
                initTransactionHistoryRecycler();
            }
        }
    }

    List<BtgTxResponse> txListAll = new ArrayList<>();
    AtomicInteger txCountToLoad = new AtomicInteger(0);
    AtomicInteger txLoaded = new AtomicInteger(0);

    private void loadAllTransactions(Callback<List<BtgTxResponse>> onComplete) {
        //Log.d("flint", "loadAllTransactions()...");
        txListAll = new ArrayList<>();
        txCountToLoad = new AtomicInteger(0);
        txLoaded = new AtomicInteger(0);

        loadNextTransactions(onComplete);
    }

    private void loadNextTransactions(Callback<List<BtgTxResponse>> onComplete) {

        if (walletManager.getWalletFriendlyAddress() == null) {
            Toast.makeText(getActivity(), "Can't get transactions. Your address is empty", Toast.LENGTH_LONG).show();
            return;
        }

        int loaded = txLoaded.get();
        RequestorBtc.getTransactionsKmdNew(walletManager.getWalletFriendlyAddress(), loaded, loaded+50, new ApiMethods.RequestListener() {
            @Override
            public void onSuccess(Object response) {
                KmdTxListResponse txListResponse = (KmdTxListResponse) response;
                if (loaded >= txListAll.size())
                    for (BtgTxResponse tx : txListResponse.getTxs())
                        txListAll.add(tx);
                txLoaded.set(txListAll.size());
                txCountToLoad.set(txListResponse.getPagesTotal());
                //Log.d("flint", "txLoaded: " + txLoaded + ", txCountToLoad: " + txCountToLoad);
                if (txLoaded.get() < txCountToLoad.get())
                    loadNextTransactions(onComplete);
                else
                    onComplete.onResponse(txListAll);
            }

            @Override
            public void onFailure(String msg) {
                loaderAnimation.cancel();
                if (isAdded()) {
                    ((MainActivity) getActivity()).showCustomToast(getStringIfAdded(R.string.err_get_history), R.drawable.err_history);
                }
            }
        });
    }

    private void loadTransactions() {
        loadAllTransactions((txList)->{
            List<TransactionItem> txItems = transactionsManager.transformTxToFriendlyNew(txList, walletManager.getWalletFriendlyAddress());
            transactionsManager.setTransactionsList(txItems);
            showTransactions();
            updateEstimateInterest(txList);
        });
    }

    private void updateEstimateInterest(List<BtgTxResponse> txList) {
        if (walletManager.getWalletFriendlyAddress() == null) {
            return;
        }

        walletManager.getEstimateInterest(txList, (interest) -> {
            curInterest = interest.getValue();
            if (interest.getValue() > 0) {
                tvYouHaveToClaimInterest.setText(interest.toPlainString() + " " + Common.MAIN_CURRENCY.toUpperCase());
                tvInterestClaimed.setVisibility(View.GONE);
                tvYouHaveToClaim.setVisibility(View.VISIBLE);
                tvYouHaveToClaimInterest.setVisibility(View.VISIBLE);
                btnClaimNow.setEnabled(true);
                btnClaimNow.setAlpha(1.0f);
                btnClaimNow.setText("CLAIM NOW");
            } else {
                tvInterestClaimed.setVisibility(View.VISIBLE);
                tvInterestClaimed.setText("No funds available");
                tvYouHaveToClaim.setVisibility(View.GONE);
                tvYouHaveToClaimInterest.setVisibility(View.GONE);
                btnClaimNow.setEnabled(false);
                btnClaimNow.setAlpha(0.5f);
                btnClaimNow.setText("CLAIM NOW");
            }
        });
    }

    private void loadBalance() {
        RequestorBtc.getBalanceKmdNew(walletManager.getWalletFriendlyAddress(), new ApiMethods.RequestListener() {
            @Override
            public void onSuccess(Object response) {
                loaderAnimation.cancel();
                ResponseBody balance = (ResponseBody) response;
                try {
                    String strBalance = balance.string();
                    walletManager.setMyBalance(Long.parseLong(strBalance));
                    walletManager.setBalance(Long.parseLong(strBalance));
                } catch (Exception e) {
                    walletManager.setBalance(0l);
                }
                String curBalance = WalletManager.getFriendlyBalance(walletManager.getMyBalance());
                setCryptoBalance(curBalance);
                getLocalBalance(curBalance);
            }

            @Override
            public void onFailure(String msg) {
                loaderAnimation.cancel();
                if (isAdded() && !isDetached()) {
                    ((MainActivity) getActivity()).showCustomToast(getStringIfAdded(R.string.err_get_balance), R.drawable.err_balance);
                }
            }
        });

    }

    private void getLocalBalance(final String balance) {
        CoinmarketcapHelper.getExchange(Common.MAIN_CURRENCY_NAME,
                sharedManager.getLocalCurrency().toLowerCase(),
                new ApiMethods.RequestListener() {
                    @Override
                    public void onSuccess(Object response) {
                        List<RespExch> exchange = (List<RespExch>) response;

                        String localBalance = balance.replace(",", "");

                        if (exchange.get(0).getPrice(sharedManager.getLocalCurrency().toLowerCase()) != null) {
                            Double res = Double.valueOf(localBalance) * (Double.valueOf(exchange.get(0).getPrice(sharedManager.getLocalCurrency().toLowerCase())));
                            setUSDBalance(Double.toString(round(res, 2)));
                        } else {
                            setUSDBalance("...");
                        }
                    }

                    @Override
                    public void onFailure(String msg) {
                        loaderAnimation.cancel();
                    }
                });
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private void checkFromRestore() {
        Bundle args = getArguments();
        if (args != null) {
            String key = args.getString(KEY);
            if (!TextUtils.isEmpty(key)) {
                showProgress(getStringIfAdded(R.string.restoring_wallet));
                if (key.substring(0, 4).equalsIgnoreCase("xprv")) {
                    walletManager.restoreFromBlockByXPRV(key, new WalletCreationCallback() {
                        @Override
                        public void onWalletCreated(Object walletFile) {
                            if (isVisible) {
                                closeProgress();
                                showBalance(true);
                            }
                        }
                    });
                } else {
                    walletManager.restoreFromBlock(key, new WalletCreationCallback() {
                        @Override
                        public void onWalletCreated(Object walletFile) {
                            if (isVisible) {
                                closeProgress();
                                showBalance(true);
                            }
                        }
                    });
                }
            }
        }
    }

    private void initTransactionHistoryRecycler() {
        TransHistoryAdapter adapter = new TransHistoryAdapter();
        new Handler();
        adapter.setItemClickListener(new TransHistoryAdapter.OnItemClickListener() {
            @Override
            public void OnItemClick(int position) {
                Intent detailsIntent = new Intent(getActivity(), TransactionDetailsActivity.class);
                detailsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                detailsIntent.putExtra(EXTRA_TRANSACTION_POSITION, position);
                startActivity(detailsIntent);
                getActivity().overridePendingTransition(R.anim.slide_in_left, R.anim.no_slide);
            }
        });

        final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        rvTransactionsList.setLayoutManager(layoutManager);
        rvTransactionsList.setAdapter(adapter);

    }

    private void setCryptoBalance(String balance) {
        tvCryptoCount.setText(String.format(Locale.US, "%s " + sharedManager.getCurrentCurrency().toUpperCase(),
                balance));
    }

    private void setUSDBalance(String balance) {
        tvUSDCount.setText(String.format("%s %s", balance, sharedManager.getLocalCurrency().toUpperCase()));
    }

    @OnClick({R.id.fab_buy, R.id.fab_purchase, R.id.fab_withdraw, R.id.fab_deposit})
    public void fabButtonsClick(View view) {
        MainActivity mainActivity = (MainActivity) getActivity();
        switch (view.getId()) {
            case R.id.fab_buy:
                fabMenu.close(true);
                navigateToFragment(new PurchaseServiceFragment());
                mainActivity.setToolBarTitle(R.string.app_amount_to_purchase);
                break;
            case R.id.fab_purchase:
                fabMenu.close(true);
                navigateToFragment(new ExchangeFragment());
                mainActivity.setToolBarTitle(R.string.purchase_purchase);
                break;
            case R.id.fab_withdraw:
                fabMenu.close(true);
                navigateToFragment(new WithdrawFragment());
                mainActivity.setToolBarTitle(R.string.withdraw_address_send);
                break;
            case R.id.fab_deposit:
                fabMenu.close(true);
                navigateToFragment(new DepositFragment());
                mainActivity.setToolBarTitle(R.string.app_your_address);
                break;
        }
    }

    @OnClick(R.id.iv_update_transactions)
    public void onUpdateClick() {
        showBalance(false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        isVisible = true;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isVisible = false;
    }

    private void updateTxsCache() {
        if (walletManager.getWalletFriendlyAddress() == null || walletManager.getWalletFriendlyAddress().equalsIgnoreCase(""))
            return;

        Gson gson = new Gson();
        Type listType = new TypeToken<Map<String, ArrayList<TransactionItem>>>(){}.getType();
        String jsonFromPref = sharedManager.getTxsCache();
        Map<String, ArrayList<TransactionItem>> addrTxsMap = gson.fromJson(jsonFromPref, listType);

        if (addrTxsMap == null)
            addrTxsMap = new HashMap<>();

        addrTxsMap.put(walletManager.getWalletFriendlyAddress(), (ArrayList<TransactionItem>) transactionsManager.getTransactionsList());
        String jsonToPref = gson.toJson(addrTxsMap);
        sharedManager.setTxsCache(jsonToPref);
    }

    private void askRating() {
        if (!sharedManager.getIsAskRate() || transactionsManager.getTransactionsList().size() < 3) {
            return;
        }

        int nextShowRate = sharedManager.getNextShowRate();
        int nowCount = 0;
        for (TransactionItem tr : transactionsManager.getTransactionsList()) {
            if (tr.getConfirmations() >= TransHistoryAdapter.MIN_CONFIRMATIONS) {
                nowCount++;
            }
        }

        if (nextShowRate == 0) {
            sharedManager.setNextShowRate(nowCount + 3);
            return;
        } else if (nowCount >= nextShowRate) {
            if (sharedManager.getIsWaitFourTr()) {
                showRateDialog(nowCount + 4);
                sharedManager.setIsWaitFourTr(false);
            } else {
                showRateDialog(nowCount + 7);
            }
        }
    }

    private void showRateDialog(int inc) {
        if (isAdded()) {
            RateDialog rateDialog = new RateDialog();
            rateDialog.show(getActivity().getFragmentManager(), "RateDialog");
            sharedManager.setNextShowRate(inc);
        }
    }

    private void initRotation(ImageView ivLoader) {
        if (loaderAnimation == null) {
            loaderAnimation = ObjectAnimator.ofFloat(ivLoader, "rotation", 0.0f, 360f);
            loaderAnimation.setDuration(1500);
            loaderAnimation.setRepeatCount(ObjectAnimator.INFINITE);
            loaderAnimation.setInterpolator(new LinearInterpolator());
            loaderAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationCancel(animation);
                    tvUpdateTransactions.setClickable(true);
                }
            });
        }
    }

    private void startClockwiseRotation() {
        if (!loaderAnimation.isRunning()) {
            loaderAnimation.start();
        }
    }

    public void scrollToTop() {
        nsvMainScrollLayout.scrollTo(0, 0);
    }

    private String getStringIfAdded(int resId) {
        if (isAdded()) {
            return getString(resId);
        } else {
            return "";
        }
    }

}
