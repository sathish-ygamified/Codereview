/**
 * @author Suneetha Nandhimandalam
 *
 */
package com.hpe.controllers.pages;

import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractPageController;
import de.hybris.platform.multicountry.facades.storesession.impl.MulticountryStoreSessionFacade;
import de.hybris.platform.util.Config;

import javax.annotation.Resource;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.hpe.constants.HPEIntegrationsConstant;
import com.hpe.facades.country.HPECountryFacade;
import com.hpe.facades.storesession.moxie.data.HPEMoxieLiveChatData;


/**
 * Controller for home page
 */

public class HPEAbstractPageController extends AbstractPageController
{
	@Resource(name = "storeSessionFacade")
	private MulticountryStoreSessionFacade multicountryStoreSessionFacade;

	@Resource(name = "hpeCountryFacade")
	private HPECountryFacade hpeCountryFacade;

	/**
	 * This method returns HPEMoxieLiveChatData which contains DetectionId,QueueId and TalCustProp based on country.
	 *
	 * @param model
	 * @return HPEMoxieLiveChatData
	 */
	@ModelAttribute("moxie")
	public HPEMoxieLiveChatData getMoxieLiveChat(final Model model)
	{
		final HPEMoxieLiveChatData moxieData = multicountryStoreSessionFacade.getMoxieLiveChat();
		final String moxieLiveChatUrl = Config.getParameter(HPEIntegrationsConstant.Moxie_LiveChat_Url);
		model.addAttribute("moxie", moxieData);
		model.addAttribute("moxieBaseUrl", moxieLiveChatUrl);
		return moxieData;
	}


	/**
	 * @return the hpeCountryFacade
	 */
	public HPECountryFacade getHpeCountryFacade()
	{
		return hpeCountryFacade;
	}

	/**
	 * @param hpeCountryFacade
	 *           the hpeCountryFacade to set
	 */
	public void setHpeCountryFacade(final HPECountryFacade hpeCountryFacade)
	{
		this.hpeCountryFacade = hpeCountryFacade;
	}


	/**
	 * @return the multicountryStoreSessionFacade
	 */
	public MulticountryStoreSessionFacade getMulticountryStoreSessionFacade()
	{
		return multicountryStoreSessionFacade;
	}

	/**
	 * @param multicountryStoreSessionFacade
	 *           the multicountryStoreSessionFacade to set
	 */
	public void setMulticountryStoreSessionFacade(final MulticountryStoreSessionFacade multicountryStoreSessionFacade)
	{
		this.multicountryStoreSessionFacade = multicountryStoreSessionFacade;
	}


}
