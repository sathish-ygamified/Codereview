/*
 * [y] hybris Platform
 *
 * Copyright (c) 2018 SAP SE or an SAP affiliate company.  All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package com.hpe.controllers.pages;

import de.hybris.platform.acceleratorfacades.ordergridform.OrderGridFormFacade;
import de.hybris.platform.acceleratorfacades.product.data.ReadOnlyOrderGridData;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.Breadcrumb;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.UpdateQuantityForm;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.comment.data.CommentData;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.QuoteFacade;
import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.quote.data.QuoteData;
import de.hybris.platform.commercefacades.voucher.VoucherFacade;
import de.hybris.platform.commerceservices.enums.QuoteAction;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.search.pagedata.SearchPageData;
import de.hybris.platform.commerceservices.util.QuoteExpirationTimeUtils;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.servicelayer.exceptions.ModelNotFoundException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.validation.Valid;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hpe.checkout.steps.validation.impl.HPEDefaultSendQuoteEmailFormValidator;
import com.hpe.controllers.ControllerConstants;
import com.hpe.facades.orders.data.HPEOrdersData;
import com.hpe.facades.quote.data.HPEQuoteSendEmailFormData;
import com.hpe.facades.quote.impl.HPEDefaultQuoteFacade;
import com.hpe.facades.user.HPEUserFacade;
import com.hpe.quote.form.HPEQuoteSendEmailForm;


@Controller
@RequestMapping(value = "/my-account/my-quotes")
public class MyQuotesController extends AbstractSearchPageController
{
	private static final Logger LOG = Logger.getLogger(MyQuotesController.class);

	private static final String MY_QUOTES_LIST_CMS_PAGE = "accountMyQuotesListPage";
	protected static final String QUOTE_DETAILS_CMS_PAGE_LABEL = "quotedetails.page.label";
	private static final String QUOTE_DETAILS_CMS_PAGE = "quote-detail";
	private static final String REDIRECT_QUOTE_LIST_URL = REDIRECT_PREFIX + "/my-account/my-quotes/";
	protected static final String QUOTE_CREATE_HPE_QUOTE_CMS_PAGE_LABEL = "quote.create.page.label";
	private static final String PAGINATION_NUMBER_OF_COMMENTS = "quote.pagination.numberofcomments";
	private static final String ALLOWED_ACTIONS = "allowedActions";
	private static final String SYSTEM_ERROR_PAGE_NOT_FOUND = "system.error.page.not.found";
	private static final String QUOTE_CART_INSUFFICIENT_ACCESS_RIGHTS = "quote.cart.insufficient.access.rights.error";
	private static final String MYACCOUNT_MYQUOTES_BREADCRUMB = "/my-account/my-quotes";
	private static final String MYACCOUNT_MANAGEQUOTES_BREADCRUMB = "text.account.manageQuotes.breadcrumb";
	private static final String HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE = "hpeCreateSendQuoteViaEmailPage";
	private static final String REDIRECT_HPE_SENDQUOTE_EMAIL_URL = REDIRECT_PREFIX + "/my-account/my-quotes/%s";

	private static final String REDIRECT_SENDQUOTE_EMAIL_VIEW_URL = REDIRECT_PREFIX + "/my-account/my-quotes/sendQuoteEmail";;



	@Resource(name = "accountBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

	@Resource(name = "quoteFacade")
	private QuoteFacade quoteFacade;

	@Resource(name = "voucherFacade")
	private VoucherFacade voucherFacade;

	@Resource(name = "orderGridFormFacade")
	private OrderGridFormFacade orderGridFormFacade;

	@Resource(name = "cartFacade")
	private CartFacade cartFacade;

	@Resource(name = "quoteFacade")
	private HPEDefaultQuoteFacade hpeDefaultQuoteFacade;

	@Resource(name = "hpeUserFacade")
	private HPEUserFacade hpeUserFacade;

	@Resource(name = "hpeDefaultSendQuoteEmailFormValidator")
	private HPEDefaultSendQuoteEmailFormValidator hpeDefaultSendQuoteEmailFormValidator;


	@RequestMapping(method = RequestMethod.GET)
	@RequireHardLogIn
	public String quotes(@RequestParam(value = "page", defaultValue = "0") final int page,
			@RequestParam(value = "show", defaultValue = "Page") final ShowMode showMode,
			@RequestParam(value = "sort", defaultValue = "byDate") final String sortCode, final Model model,
			@Valid final HPEOrdersData hpeQuoteData) throws CMSItemNotFoundException
	{
		// Handle paged search results
		final PageableData pageableData = createPageableData(page, 5, sortCode, showMode);
		final SearchPageData searchPageData = hpeDefaultQuoteFacade.getHPEPagedQuotes(pageableData);
		populateModel(model, searchPageData, showMode);
		boolean isQuoteExpiredFlag;
		final Date currentDate = hpeUserFacade.getCurrentLocalTime();
		Date expiryDate = null;
		final List<QuoteData> results = searchPageData.getResults();
		final List<QuoteData> quoteResults = new ArrayList<QuoteData>();
		for (final QuoteData quoteData : results)
		{
			expiryDate = quoteData.getExpirationTime();
			isQuoteExpiredFlag = isQuoteExpirationValidation(expiryDate, currentDate);
			quoteData.setIsQuoteExpiredFlag(isQuoteExpiredFlag);
			quoteResults.add(quoteData);
		}
		model.addAttribute("quoteResults", quoteResults);
		final List<Breadcrumb> breadcrumbs = accountBreadcrumbBuilder.getBreadcrumbs(null);
		breadcrumbs.add(new Breadcrumb(MYACCOUNT_MYQUOTES_BREADCRUMB,
				getMessageSource().getMessage(MYACCOUNT_MANAGEQUOTES_BREADCRUMB, null, getI18nService().getCurrentLocale()), null));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, breadcrumbs);
		storeCmsPageInModel(model, getContentPageForLabelOrId(MY_QUOTES_LIST_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(MY_QUOTES_LIST_CMS_PAGE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		LOG.info("QuoteListPage==ENDS=====>");
		return ControllerConstants.Views.Pages.Quote.HpeQuoteListPage;
	}



	@RequestMapping(value = "/{quoteCode}", method = RequestMethod.GET)
	@RequireHardLogIn
	public String quote(final Model model, final RedirectAttributes redirectModel,
			@PathVariable("quoteCode") final String quoteCode) throws CMSItemNotFoundException
	{
		try
		{
			LOG.info("QuoteDetailsPage=======quoteData====starts===");

			model.addAttribute(new HPEOrdersData());
			final QuoteData quoteData = hpeDefaultQuoteFacade.getHpeQuoteModel(quoteCode);
			model.addAttribute("quoteData", quoteData);

			model.addAttribute("hpeAcctCustomFieldsData", quoteData.getHpeOrdersAccountData().getHpeAcctCustomFieldsData());
			model.addAttribute("ordersMediaModelList", quoteData.getHpeOrdersAccountData().getOrdersMediaModelList());

			if (quoteData.getHpeOrdersAccountData().getOrdersMediaModelList() != null)
			{
				final Collection<MediaModel> quoteMediaModelList = quoteData.getHpeOrdersAccountData().getOrdersMediaModelList();
				final Iterator<MediaModel> itrMediaFile = quoteMediaModelList.iterator();
				final HashMap<String, String> ordersMediaMap = new HashMap<String, String>();
				while (itrMediaFile.hasNext())
				{
					final MediaModel mediaModel = itrMediaFile.next();
					if (mediaModel != null)
					{
						quoteData.getHpeOrdersAccountData().setOrdersMediaPK(mediaModel.getRealFileName());
						quoteData.getHpeOrdersAccountData().setOrdersMediaUrlLink(mediaModel.getDownloadURL());
						model.addAttribute("ordersMediaFileName", quoteData.getHpeOrdersAccountData().getOrdersMediaPK());
						model.addAttribute("ordersMediaFileUrl", quoteData.getHpeOrdersAccountData().getOrdersMediaUrlLink());
						ordersMediaMap.put(mediaModel.getRealFileName(), mediaModel.getDownloadURL());
						model.addAttribute("ordersMediaMap", ordersMediaMap);

					}
				}
			}


			final ContentPageModel contentPage = getCmsPageService()
					.getPageForLabel(getSiteConfigService().getString(QUOTE_DETAILS_CMS_PAGE_LABEL, "quote-detail"));
			LOG.info("QuoteDetailsPage=======contentPage===" + contentPage);
			model.addAttribute("quote-detail", contentPage);

			storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_DETAILS_CMS_PAGE));
			setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_DETAILS_CMS_PAGE));

			final List<Breadcrumb> breadcrumbs = accountBreadcrumbBuilder.getBreadcrumbs(null);
			breadcrumbs.add(new Breadcrumb(MYACCOUNT_MYQUOTES_BREADCRUMB,
					getMessageSource().getMessage(MYACCOUNT_MANAGEQUOTES_BREADCRUMB, null, getI18nService().getCurrentLocale()),
					null));
			breadcrumbs.add(new Breadcrumb("/" + urlEncode(quoteCode) + "/",
					getMessageSource().getMessage("breadcrumb.quote.view", new Object[]
					{ quoteCode }, "Quote {0}", getI18nService().getCurrentLocale()), null));
			model.addAttribute(WebConstants.BREADCRUMBS_KEY, breadcrumbs);
			model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
			LOG.info("QuoteDetailsPage==ENDS=====>");
			return ControllerConstants.Views.Pages.Quote.HpeQuoteDetailsPlaceOrderPage;
		}
		catch (final UnknownIdentifierException e)
		{
			LOG.warn("Unable to load cart for quote " + quoteCode, e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, SYSTEM_ERROR_PAGE_NOT_FOUND, null);
			return REDIRECT_QUOTE_LIST_URL;
		}
		catch (final ModelNotFoundException e)
		{
			LOG.warn("Attempted to load a quote that does not exist or is not accessible by current user:" + quoteCode, e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					QUOTE_CART_INSUFFICIENT_ACCESS_RIGHTS, new Object[]
					{ getCartFacade().getSessionCart().getCode() });
			return REDIRECT_PREFIX + ROOT;
		}

	}

	/***
	 * Delete the Quote
	 *
	 * @param model
	 * @param redirectModel
	 * @param quoteCode
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@ResponseBody
	@RequestMapping(value = "/{quoteCode}/deleteQuoteDetail", method = RequestMethod.GET)
	@RequireHardLogIn
	private String deleteHPEQuoteDetail(final Model model, final RedirectAttributes redirectModel,
			@PathVariable("quoteCode") final String quoteCode) throws CMSItemNotFoundException
	{
		String response = "success";
		try
		{
			LOG.info("deleteHPEQuoteDetail==quoteCode:::" + quoteCode);
			hpeDefaultQuoteFacade.deleteHpeQuote(quoteCode);
		}
		catch (final Exception e)
		{
			LOG.error("Quote Details - Hide Quote Exception occured !!!, the exception is " + e.getMessage());
			response = "failure";
		}
		GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "text.quote.order.delete.status",
				new Object[]
				{ quoteCode });
		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_DETAILS_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_DETAILS_CMS_PAGE));
		return response;
	}

	/***
	 *
	 * @param model
	 * @param hpeQuoteSendEmailForm
	 * @param hpeQuoteSendEmailFormData
	 * @param redirectModel
	 * @return
	 * @throws CMSItemNotFoundException
	 */


	@RequireHardLogIn
	@RequestMapping(value = "/{quoteCode}/initSendQuoteEmail", method = RequestMethod.GET)
	public String initSendQuoteEmail(final Model model, final RedirectAttributes redirectModel,
			@PathVariable("quoteCode") final String quoteCode, @Valid final HPEQuoteSendEmailForm hpeQuoteSendEmailForm)
			throws CMSItemNotFoundException
	{
		LOG.info("initSendQuoteEmail method starts");
		model.addAttribute(new HPEQuoteSendEmailForm());
		hpeQuoteSendEmailForm.setQuoteCode(quoteCode);
		model.addAttribute("hpeQuoteSendEmailForm", hpeQuoteSendEmailForm);
		storeCmsPageInModel(model, getContentPageForLabelOrId(HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE));
		return ControllerConstants.Views.Pages.Quote.HpeSendQuoteEmailPage;


	}

	/***
	 *
	 * @param model
	 * @param hpeQuoteSendEmailForm
	 * @param hpeQuoteSendEmailFormData
	 * @param redirectModel
	 * @param bindingResult
	 * @param sessionStatus
	 * @return
	 * @throws CMSItemNotFoundException
	 */


	@RequireHardLogIn
	@RequestMapping(value = "/sendQuoteEmail", method =
	{ RequestMethod.POST, RequestMethod.GET })
	@ResponseBody
	public String sendQuoteEmail(final Model model, @Valid final HPEQuoteSendEmailForm hpeQuoteSendEmailForm,
			@ModelAttribute final HPEQuoteSendEmailFormData hpeQuoteSendEmailFormData, final RedirectAttributes redirectModel,
			final BindingResult bindingResult) throws CMSItemNotFoundException
	{
		LOG.info("sendQuoteEmail method starts");

		String response = "success";
		model.addAttribute(new HPEQuoteSendEmailFormData());
		final String quoteCode = hpeQuoteSendEmailForm.getQuoteCode();

		hpeDefaultSendQuoteEmailFormValidator.validate(hpeQuoteSendEmailForm, bindingResult);
		if (bindingResult.hasErrors())
		{
			for (final ObjectError error : bindingResult.getAllErrors())
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, error.getCode());
			}
			redirectModel.addFlashAttribute("hpeQuoteSendEmailForm", hpeQuoteSendEmailForm);
			return REDIRECT_SENDQUOTE_EMAIL_VIEW_URL;
		}

		final String[] emailids = hpeQuoteSendEmailForm.getHpeEmailIds().split(",");
		final List<String> hpeQuoteEmails = Arrays.asList(emailids);
		hpeQuoteSendEmailFormData.setHpeSubjectLine(hpeQuoteSendEmailForm.getHpeSubjectLine());
		hpeQuoteSendEmailFormData.setHpeEmailIds(hpeQuoteSendEmailForm.getHpeEmailIds());
		hpeQuoteSendEmailFormData.setHpeComments(hpeQuoteSendEmailForm.getHpeComments());
		hpeQuoteSendEmailFormData.setHpeEmailIdsList(hpeQuoteEmails);

		model.addAttribute("hpeQuoteSendEmailForm", hpeQuoteSendEmailForm);
		model.addAttribute("hpeQuoteSendEmailFormData", hpeQuoteSendEmailFormData);

		try
		{
			hpeDefaultQuoteFacade.sendQuoteAsEmail(hpeQuoteSendEmailFormData, quoteCode);
		}
		catch (final Exception e)
		{
			// XXX Auto-generated catch block
			LOG.error(" SendQuote Email Exception occured !!!, the exception is " + e.getMessage());
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"Error occurred while SendQuote to mail ", null);
			response = "failure";
		}

		storeCmsPageInModel(model, getContentPageForLabelOrId(HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE));

		GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "text.quote.sendemail.success",
				new Object[]
				{ quoteCode });

		LOG.info("sendQuoteEmail method ends");
		return REDIRECT_HPE_SENDQUOTE_EMAIL_URL;

	}

	/***
	 * Delete the selected Quotes
	 *
	 * @param model
	 * @param redirectModel
	 * @param quoteCode
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@RequestMapping(value = "/deleteQuotesList", method = RequestMethod.POST)
	@RequireHardLogIn
	@ResponseBody
	private String deleteHPEQuoteListing(final Model model, final RedirectAttributes redirectModel,
			@RequestParam("hideQuoteArray") final String quoteCodes) throws CMSItemNotFoundException
	{
		String response = "success";
		LOG.info("deleteHPEQuoteListing==quoteCode:::" + quoteCodes);
		try
		{
			final String[] quoteCodesArray = quoteCodes.split(",");
			hpeDefaultQuoteFacade.deleteHpeQuoteList(quoteCodesArray);
		}

		catch (final Exception e)
		{
			LOG.error("Quote Listing - Hide the Selected Quotes Exception occured !!!, the exception is " + e.getMessage());
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"Set the Quote Delete status is failed.. ", null);
			response = "failure";
		}
		GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "text.quote.order.delete.status",
				new Object[]
				{ quoteCodes });
		return response;
	}

	/**
	 * Set the CartData entries in the model object.
	 *
	 * @author shaaiee
	 * @param hpeQuoteForm
	 * @param model
	 * @throws CMSItemNotFoundException
	 */
	private void setQuoteCartDataInfoView(final Model model) throws CMSItemNotFoundException
	{
		LOG.info("setQuoteCartDataInfo method STARTS----");
		final CartData cartData = getCartFacade().getSessionCartWithEntryOrdering(false);
		createProductEntryList(model, cartData);

		LOG.info("setQuoteCartDataInfo method ENDS----");

	}

	protected void createProductEntryList(final Model model, final CartData cartData)
	{
		boolean hasPickUpCartEntries = false;
		if (cartData.getEntries() != null && !cartData.getEntries().isEmpty())
		{
			for (final OrderEntryData entry : cartData.getEntries())
			{
				if (!hasPickUpCartEntries && entry.getDeliveryPointOfService() != null)
				{
					hasPickUpCartEntries = true;
				}
				final UpdateQuantityForm uqf = new UpdateQuantityForm();
				uqf.setQuantity(entry.getQuantity());

			}
		}

		model.addAttribute("cartData", cartData);

	}

	/**
	 * Validate the Quote Expiration
	 *
	 * @param expiryDate
	 * @param currentDate
	 * @return
	 */

	private boolean isQuoteExpirationValidation(final Date expiryDate, final Date currentDate)
	{ //final boolean

		boolean isExpired = false;
		//LOG.info("Current Date ::: " + new SimpleDateFormat("yyyy/MM/dd").format(currentDate));
		//LOG.info("Expiry Date ::: " + new SimpleDateFormat("yyyy/MM/dd").format(expiryDate));
		if (expiryDate != null && currentDate != null)
		{
			if (expiryDate.compareTo(currentDate) > 0)
			{
				LOG.info("expiryDate is after currentDate== dec");
				return isExpired;
			}
			else if (expiryDate.compareTo(currentDate) < 0)
			{
				LOG.info("expiryDate is before currentDate..its expiry");
				isExpired = true;
				return isExpired;
			}
			else if (expiryDate.compareTo(currentDate) == 0)
			{
				LOG.info("expiryDate is equal to currentDate");
				return isExpired;
			}
		}
		return isExpired;
	}

	private String renderSendQuoteEmailPage(final Model model) throws CMSItemNotFoundException
	{
		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_DETAILS_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_DETAILS_CMS_PAGE));
		return ControllerConstants.Views.Pages.Quote.HpeSendQuoteEmailPage;
	}

	@RequestMapping(value = "/{quoteCode}/getReadOnlyProductVariantMatrix", method = RequestMethod.GET)
	@RequireHardLogIn
	public String getProductVariantMatrixForResponsive(@PathVariable("quoteCode") final String quoteCode,
			@RequestParam("productCode") final String productCode, final Model model)
	{
		final QuoteData quoteData = getQuoteFacade().getQuoteForCode(quoteCode);
		final Map<String, ReadOnlyOrderGridData> readOnlyMultiDMap = orderGridFormFacade.getReadOnlyOrderGridForProductInOrder(
				productCode, Arrays.asList(ProductOption.BASIC, ProductOption.CATEGORIES), quoteData);
		model.addAttribute("readOnlyMultiDMap", readOnlyMultiDMap);

		return ControllerConstants.Views.Fragments.Checkout.ReadOnlyExpandedOrderForm;
	}

	protected void sortComments(final AbstractOrderData orderData)
	{
		if (orderData != null)
		{
			if (CollectionUtils.isNotEmpty(orderData.getComments()))
			{
				final List<CommentData> sortedComments = orderData.getComments().stream()
						.sorted((comment1, comment2) -> comment2.getCreationDate().compareTo(comment1.getCreationDate()))
						.collect(Collectors.toList());
				orderData.setComments(sortedComments);
			}

			if (CollectionUtils.isNotEmpty(orderData.getEntries()))
			{
				for (final OrderEntryData orderEntry : orderData.getEntries())
				{
					if (CollectionUtils.isNotEmpty(orderEntry.getComments()))
					{
						final List<CommentData> sortedEntryComments = orderEntry.getComments().stream()
								.sorted((comment1, comment2) -> comment2.getCreationDate().compareTo(comment1.getCreationDate()))
								.collect(Collectors.toList());

						orderEntry.setComments(sortedEntryComments);
					}
					else if (orderEntry.getProduct() != null && orderEntry.getProduct().getMultidimensional() != null
							&& orderEntry.getProduct().getMultidimensional())
					{
						if (CollectionUtils.isNotEmpty(orderEntry.getEntries()))
						{
							for (final OrderEntryData multiDOrderEntry : orderEntry.getEntries())
							{
								if (CollectionUtils.isNotEmpty(multiDOrderEntry.getComments()))
								{
									final List<CommentData> sortedMultiDOrderEntryComments = multiDOrderEntry.getComments().stream()
											.sorted((comment1, comment2) -> comment2.getCreationDate().compareTo(comment1.getCreationDate()))
											.collect(Collectors.toList());

									multiDOrderEntry.setComments(sortedMultiDOrderEntryComments);
								}
							}
						}
					}
				}
			}
		}
	}

	protected void loadCommentsShown(final Model model)
	{
		final int commentsShown = getSiteConfigService().getInt(PAGINATION_NUMBER_OF_COMMENTS, 5);
		model.addAttribute("commentsShown", Integer.valueOf(commentsShown));
	}

	/**
	 * Set allowed actions for a given quote on model.
	 *
	 * @param model
	 *           the MVC model
	 * @param quoteCode
	 *           the quote to be checked.
	 */
	protected void setAllowedActions(final Model model, final String quoteCode)
	{
		final Set<QuoteAction> actionSet = getQuoteFacade().getAllowedActions(quoteCode);

		if (actionSet != null)
		{
			final Map<String, Boolean> actionsMap = actionSet.stream()
					.collect(Collectors.toMap((v) -> v.getCode(), (v) -> Boolean.TRUE));
			model.addAttribute(ALLOWED_ACTIONS, actionsMap);
		}
	}


	protected void setExpirationTimeAttributes(final Model model)
	{
		model.addAttribute("defaultOfferValidityPeriodDays",
				Integer.valueOf(QuoteExpirationTimeUtils.getDefaultOfferValidityPeriodDays()));
		model.addAttribute("minOfferValidityPeriodDays",
				Integer.valueOf(QuoteExpirationTimeUtils.getMinOfferValidityPeriodInDays()));
	}


	private void getOrderHistoryCount(final Model model) //get value from config and add to model
	{
		final String ORDER_COUNT_OPTIONS = "10";
		final String optionCount = getSiteConfigService().getString(ORDER_COUNT_OPTIONS, "10,20,50,100");
		final String[] words = optionCount.split(",");
		final int options[] = new int[words.length];
		for (int i = 0; i < options.length; i++)
		{
			options[i] = Integer.parseInt(words[i]);
		}
		model.addAttribute("options", options);

	}


	protected QuoteFacade getQuoteFacade()
	{
		return quoteFacade;
	}

	public VoucherFacade getVoucherFacade()
	{
		return voucherFacade;
	}

	protected CartFacade getCartFacade()
	{
		return cartFacade;
	}

	public HPEDefaultQuoteFacade getHpeDefaultQuoteFacade()
	{
		return hpeDefaultQuoteFacade;
	}

	public void setHpeDefaultQuoteFacade(final HPEDefaultQuoteFacade hpeDefaultQuoteFacade)
	{
		this.hpeDefaultQuoteFacade = hpeDefaultQuoteFacade;
	}

	/**
	 * @return the hpeUserFacade
	 */
	public HPEUserFacade getHpeUserFacade()
	{
		return hpeUserFacade;
	}

	/**
	 * @param hpeUserFacade
	 *           the hpeUserFacade to set
	 */
	public void setHpeUserFacade(final HPEUserFacade hpeUserFacade)
	{
		this.hpeUserFacade = hpeUserFacade;
	}
}
