package org.multibit.hd.ui.views.wizards.credentials;

import com.google.common.base.Optional;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.hardware.core.messages.FailureType;
import org.multibit.hd.hardware.core.messages.Features;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Components;
import org.multibit.hd.ui.views.components.ModelAndView;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.components.trezor_display.TrezorDisplayModel;
import org.multibit.hd.ui.views.components.trezor_display.TrezorDisplayView;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;

import javax.swing.*;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Credentials: Request cipher key</li>
 * </ul>
 * <p>This is the first step in getting the extended public key from a Trezor device</p>
 *
 * @since 0.0.1
 *  
 */
public class CredentialsRequestCipherKeyPanelView extends AbstractWizardPanelView<CredentialsWizardModel, String> {

  private ModelAndView<TrezorDisplayModel, TrezorDisplayView> trezorDisplayMaV;
  private Optional<FailureType> failureType = Optional.absent();

  /**
   * @param wizard The wizard managing the states
   */
  public CredentialsRequestCipherKeyPanelView(AbstractWizard<CredentialsWizardModel> wizard, String panelName) {

    super(wizard, panelName, MessageKey.TREZOR_UNLOCK_TITLE, AwesomeIcon.LOCK);

  }

  @Override
  public void newPanelModel() {

    // Bind it to the wizard model in case of failure
    getWizardModel().setRequestCipherKeyPanelView(this);

  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(
      new MigLayout(
        Panels.migXLayout(),
        "[]", // Column constraints
        "[]" // Row constraints
      ));

    trezorDisplayMaV = Components.newTrezorDisplayMaV(getPanelName());
    contentPanel.add(trezorDisplayMaV.getView().newComponentPanel(), "align center,wrap");

    // Register the components
    registerComponents(trezorDisplayMaV);

  }

  @Override
  protected void initialiseButtons(AbstractWizard<CredentialsWizardModel> wizard) {

    PanelDecorator.addExitCancelRestoreNext(this, wizard);

  }

  @Override
  public void fireInitialStateViewEvents() {

    // Initialise with "Unlock" disabled to force users to work with Trezor
    ViewEvents.fireWizardButtonEnabledEvent(
      getPanelName(),
      WizardButton.NEXT,
      false
    );

  }

  @Override
  public void afterShow() {

    // Check if the attached Trezor is initialised (the hardware wallet service must be OK to be here)
    Optional<Features> features = CoreServices.getOrCreateHardwareWalletService().get().getContext().getFeatures();

    final MessageKey operationKey;
    final boolean nextEnabled;

    if (failureType.isPresent()) {
      switch (failureType.get()) {

        case NOT_INITIALIZED:
          operationKey = MessageKey.TREZOR_NO_WALLET_OPERATION;
          nextEnabled = true;
          break;
        default:
          operationKey = MessageKey.TREZOR_FAILURE_OPERATION;
          nextEnabled = true;
      }
    } else {

      if (!features.isPresent()) {
        operationKey = MessageKey.TREZOR_FAILURE_OPERATION;
        nextEnabled = true;
      } else {
        if (features.get().isInitialized()) {
          operationKey = MessageKey.COMMUNICATING_WITH_TREZOR_OPERATION;
          // May take some time
          nextEnabled = false;
        } else {
          operationKey = MessageKey.TREZOR_NO_WALLET_OPERATION;
          nextEnabled = true;
        }
      }
    }

    SwingUtilities.invokeLater(
      new Runnable() {
        @Override
        public void run() {

          // Set the communication message
          trezorDisplayMaV.getView().setOperationText(operationKey);

          if (nextEnabled) {
            trezorDisplayMaV.getView().setRecoveryText(MessageKey.CLICK_NEXT_TO_CONTINUE);
          }

          if (!failureType.isPresent()) {
            // This could take a while (device may tarpit after failed PINs etc)
            trezorDisplayMaV.getView().setSpinnerVisible(!nextEnabled);
            trezorDisplayMaV.getView().setRecoveryText(MessageKey.CLICK_NEXT_TO_CONTINUE);
          } else {
            // No spinner on a failure
            trezorDisplayMaV.getView().setSpinnerVisible(false);
            trezorDisplayMaV.getView().setRecoveryText(MessageKey.TREZOR_FAILURE_RECOVERY);
          }

          // Override the earlier button enable setting
          ViewEvents.fireWizardButtonEnabledEvent(
            getPanelName(),
            WizardButton.NEXT,
            nextEnabled
          );

        }
      });

    // Update the wizard model so we can change state
    getWizardModel().setSwitchToPassword(nextEnabled);

    if (!failureType.isPresent() && !nextEnabled) {

      // Start the wallet access process by requesting a cipher key
      // to get a deterministic wallet ID
      //
      // This is done as a transitional panel to allow for a device
      // failure at each stage with the user having the option to
      // easily escape
      getWizardModel().requestCipherKey();

    }

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {

    // Do nothing we are a transitional view

  }

  /**
   * @param key The key to the operation text
   */
  public void setOperationText(MessageKey key) {
    this.trezorDisplayMaV.getView().setOperationText(key);
  }

  /**
   * Sometimes a wiped Trezor will still indicate that it is initialised leading to an infinite loop
   * having a failure type allows this to be detected
   *
   * @param failureType The device failure type
   */
  public void setFailureType(FailureType failureType) {
    this.failureType = Optional.fromNullable(failureType);
  }
}