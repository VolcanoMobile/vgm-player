package net.volcanomobile.vgmplayer.ui.equalizer;

import android.databinding.DataBindingUtil;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.databinding.FragmentEqualizerBinding;
import net.volcanomobile.vgmplayer.effects.AudioEffects;
import net.volcanomobile.vgmplayer.utils.Handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by Philippe Simons on 5/11/17.
 */

public class EqualizerFragment extends Fragment {

    private final EqualizerFragmentModel mModel = new EqualizerFragmentModel();

    private AudioEffects mAudioEffects;
    private FragmentEqualizerBinding mBinding;

    public static EqualizerFragment newInstance() {
        return new EqualizerFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioEffects = AudioEffects.getInstance(getActivity());
        mAudioEffects.addObserver(mAudioEffectsObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAudioEffects.deleteObserver(mAudioEffectsObserver);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final FragmentEqualizerBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.fragment_equalizer, container, false);
        binding.setModel(mModel);

        mModel.setHasBassBoost(AudioEffects.hasBassBoost() && mAudioEffects.getSessionId() != 0);
        mModel.setHasEqualizer(AudioEffects.hasEqualizer() && mAudioEffects.getSessionId() != 0);

        binding.switchBassBoost.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        setBassBoostEnabled(isChecked);
                    }
                }
        );

        binding.seekBarBassBoost.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setBassBoostStrength(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        binding.equalizerView.setOnBandChangeListener(new EqualizerView.OnBandChangeListener() {
            @Override
            public void onBandChange(short band, short value) {
                binding.presets.setSelection(0);
            }
        });

        binding.switchEqualizer.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        setEqualizerEnabled(isChecked);
                    }
                }
        );

        mBinding = binding;
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        mModel.setBassBoostStrength(mAudioEffects.getBassBoostStrength());
        mModel.setBassBoostEnabled(mAudioEffects.isBassBoostEnabled());
        mModel.setEqualizerEnabled(mAudioEffects.isEqualizerEnabled() && mAudioEffects.getEqualizer() != null);
        mBinding.equalizerView.setEqualizer(mAudioEffects.getEqualizer());
    }

    @Override
    public void onStop() {
        super.onStop();
        mBinding.equalizerView.setEqualizer(null);
    }

    private void setBassBoostEnabled(final boolean enabled) {
        mModel.setBassBoostEnabled(enabled);
        mAudioEffects.setBassBoostEnabled(enabled);
    }

    private void setBassBoostStrength(final int strength) {
        mModel.setBassBoostStrength(strength);
        mAudioEffects.setBassBoostStrength(strength);
    }

    private void setEqualizerEnabled(final boolean enabled) {
        mModel.setEqualizerEnabled(enabled);
        mAudioEffects.setEqualizerEnabled(enabled);
        mBinding.equalizerView.setEqualizer(mAudioEffects.getEqualizer());
    }

    private final Observer mAudioEffectsObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Handlers.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mBinding.equalizerView.setEqualizer(mAudioEffects.getEqualizer());
                    mModel.setHasBassBoost(AudioEffects.hasBassBoost() && mAudioEffects.getSessionId() != 0);
                    mModel.setHasEqualizer(AudioEffects.hasEqualizer() && mAudioEffects.getSessionId() != 0);

                    if (AudioEffects.hasEqualizer()) {
                        if (mAudioEffects.getEqualizer() != null) {
                            final Equalizer equalizer = mAudioEffects.getEqualizer();
                            mModel.setEqualizerEnabled(mAudioEffects.isEqualizerEnabled());

                            List<String> presets = new ArrayList<>();
                            presets.add(getString(R.string.custom_preset));
                            for (short i = 0; i < equalizer.getNumberOfPresets(); i++) {
                                presets.add(equalizer.getPresetName(i));
                            }

                            ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, presets);
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            mBinding.presets.setAdapter(adapter);
                            mBinding.presets.setSelection(equalizer.getCurrentPreset() + 1);

                            mBinding.presets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                    if (position != 0) {
                                        equalizer.usePreset((short) (position - 1));
                                        mAudioEffects.saveEqualizerSettings(equalizer.getProperties());
                                        mBinding.equalizerView.rebuild();
                                    }
                                }

                                @Override
                                public void onNothingSelected(AdapterView<?> parent) {

                                }
                            });
                        }
                    }
                }
            });
        }
    };
}
